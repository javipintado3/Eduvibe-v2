import { Component, ContentChildren, EventEmitter, Input, OnDestroy, Output, QueryList, TemplateRef } from '@angular/core';
import { NgFor, NgIf, NgSwitch, NgSwitchCase, NgTemplateOutlet } from '@angular/common';

import { TAMANO_PAGINA } from '../../core/utils/paginacion';
import { CargandoComponent } from '../cargando/cargando.component';
import { EstadoVacioComponent } from '../estado-vacio/estado-vacio.component';
import { LimpiarFiltrosComponent } from '../limpiar-filtros/limpiar-filtros.component';
import { CeldaTablaDirective } from './celda-tabla.directive';
import { ColumnaTabla, FiltroBusqueda, FiltroTabla, OrdenTabla } from './tabla-datos.tipos';

/** Espera por defecto tras la última tecla de un buscador, antes de avisar a la pantalla. */
const RETARDO_BUSQUEDA_MS = 400;

/**
 * Tabla de datos con barra de filtros, orden, selección y paginación.
 *
 * Es el equivalente en Angular de `ThemedDataGrid` de Fortress. No carga
 * datos ni guarda el estado de los filtros: la pantalla le pasa las filas ya
 * cargadas y los filtros como una lista de objetos, y la tabla avisa con
 * eventos (`paginaCambia`, `ordenCambia`, `seleccionCambia`...) para que la
 * pantalla vuelva a pedir lo que haga falta. Así la paginación, el orden y la
 * búsqueda siguen resolviéndose en el servidor.
 *
 * Mientras hay una selección, la pantalla puede cambiar la barra de filtros
 * por su propia barra de acciones en lote (`mostrarAcciones` + contenido con
 * el atributo `acciones`), como el prop `actions` de Fortress.
 */
@Component({
  selector: 'app-tabla-datos',
  standalone: true,
  imports: [
    NgIf, NgFor, NgSwitch, NgSwitchCase, NgTemplateOutlet,
    CargandoComponent, EstadoVacioComponent, LimpiarFiltrosComponent,
  ],
  templateUrl: './tabla-datos.component.html',
  styleUrl: './tabla-datos.component.css',
})
export class TablaDatosComponent implements OnDestroy {

  @Input({ required: true }) columnas: ColumnaTabla[] = [];
  @Input({ required: true }) filas: readonly object[] = [];

  /** Identificador único de una fila; hace falta para la selección y para no repintarlas de más. */
  @Input() idFila: (fila: any) => string = (fila) => fila.id;

  @Input() cargando = false;
  @Input() filtros: FiltroTabla[] = [];

  /** Sin tarjeta propia (borde y sombra), para cuando ya va dentro de otra. */
  @Input() plana = false;

  /** Cambia la barra de filtros por el contenido marcado con el atributo `acciones`. */
  @Input() mostrarAcciones = false;

  /** Qué mostrar cuando no hay filas. */
  @Input() tituloVacio = 'No hay nada que mostrar';
  @Input() descripcionVacia = '';

  /** Las filas reaccionan al ratón y emiten `filaClic`. */
  @Input() filasClicables = false;

  // --- orden ---
  @Input() orden: OrdenTabla | null = null;
  @Output() ordenCambia = new EventEmitter<OrdenTabla | null>();

  // --- selección (el conjunto lo guarda la pantalla, que puede abarcar varias páginas) ---
  @Input() seleccionable = false;
  @Input() seleccionados: ReadonlySet<string> = new Set();
  @Output() seleccionCambia = new EventEmitter<Set<string>>();

  // --- paginación (empieza en 0, como la API) ---
  @Input() pagina = 0;
  @Input() totalPaginas = 1;
  @Input() totalElementos: number | null = null;
  @Input() etiqueta = '';
  /** Filas por página, solo para calcular el rango del pie ("1–10 de 53"). */
  @Input() tamanoPagina = TAMANO_PAGINA;
  @Output() paginaCambia = new EventEmitter<number>();

  @Output() filaClic = new EventEmitter<any>();
  @Output() limpiarFiltros = new EventEmitter<void>();

  @ContentChildren(CeldaTablaDirective) private readonly celdas?: QueryList<CeldaTablaDirective>;

  /** Un temporizador por buscador, para esperar a que se deje de escribir. */
  private readonly esperas = new Map<string, ReturnType<typeof setTimeout>>();

  ngOnDestroy(): void {
    this.esperas.forEach(clearTimeout);
  }

  /** Para que Angular reconozca cada filtro y cada fila entre repintados (no recrea sus elementos). */
  readonly claveDe = (_: number, filtro: FiltroTabla) => filtro.clave;
  readonly idDe = (_: number, fila: object) => this.idFila(fila);

  // ------------------------------------------------------------------ pie

  get desde(): number {
    return this.pagina * this.tamanoPagina + 1;
  }

  get hasta(): number {
    return Math.min(this.desde + this.filas.length - 1, this.totalElementos ?? 0);
  }

  // ------------------------------------------------------------------ filtros

  get hayFiltros(): boolean {
    return this.filtros.length > 0;
  }

  get hayFiltrosActivos(): boolean {
    return this.filtros.some(filtro => filtro.activo ?? !!filtro.valor);
  }

  alEscribir(filtro: FiltroBusqueda, valor: string): void {
    clearTimeout(this.esperas.get(filtro.clave));
    this.esperas.set(filtro.clave, setTimeout(() => filtro.alCambiar(valor), filtro.retardo ?? RETARDO_BUSQUEDA_MS));
  }

  /** Enter no espera: avisa ya con lo que haya escrito. */
  buscarYa(filtro: FiltroBusqueda, valor: string): void {
    clearTimeout(this.esperas.get(filtro.clave));
    filtro.alCambiar(valor);
  }

  // -------------------------------------------------------------------- celdas

  plantillaDe(campo: string): TemplateRef<unknown> | null {
    return this.celdas?.find(celda => celda.campo === campo)?.plantilla ?? null;
  }

  valorDe(fila: object, campo: string): unknown {
    return (fila as Record<string, unknown>)[campo];
  }

  // --------------------------------------------------------------------- orden

  direccionDe(campo: string): 'asc' | 'desc' | null {
    return this.orden?.campo === campo ? this.orden.direccion : null;
  }

  /** Cada clic avanza: sin orden → ascendente → descendente → sin orden (como MUI DataGrid). */
  alternarOrden(campo: string): void {
    const actual = this.direccionDe(campo);
    this.ordenCambia.emit(
      actual === null ? { campo, direccion: 'asc' }
      : actual === 'asc' ? { campo, direccion: 'desc' }
      : null);
  }

  // ----------------------------------------------------------------- selección

  estaSeleccionada(fila: object): boolean {
    return this.seleccionados.has(this.idFila(fila));
  }

  alternarFila(fila: object, marcada: boolean): void {
    const nuevo = new Set(this.seleccionados);
    marcada ? nuevo.add(this.idFila(fila)) : nuevo.delete(this.idFila(fila));
    this.seleccionCambia.emit(nuevo);
  }

  get todasSeleccionadas(): boolean {
    return this.filas.length > 0 && this.filas.every(fila => this.estaSeleccionada(fila));
  }

  alternarTodas(marcadas: boolean): void {
    const nuevo = new Set(this.seleccionados);
    for (const fila of this.filas) {
      marcadas ? nuevo.add(this.idFila(fila)) : nuevo.delete(this.idFila(fila));
    }
    this.seleccionCambia.emit(nuevo);
  }
}
