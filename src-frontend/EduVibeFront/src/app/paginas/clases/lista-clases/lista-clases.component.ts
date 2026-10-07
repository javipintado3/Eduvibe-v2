import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { AuthService } from '../../../core/services/auth.service';
import { ClasesService } from '../../../core/services/clases.service';
import { ConfirmacionService } from '../../../core/services/confirmacion.service';
import { Clase, Pagina } from '../../../core/models';
import { AvisoComponent } from '../../../shared/aviso/aviso.component';
import { CargandoComponent } from '../../../shared/cargando/cargando.component';
import { EstadoVacioComponent } from '../../../shared/estado-vacio/estado-vacio.component';
import { PaginadorComponent } from '../../../shared/paginador/paginador.component';
import { PlazoPipe } from '../../../shared/pipes/fecha.pipe';
import { CeldaTablaDirective } from '../../../shared/tabla-datos/celda-tabla.directive';
import { TablaDatosComponent } from '../../../shared/tabla-datos/tabla-datos.component';
import { ColumnaTabla, FiltroTabla } from '../../../shared/tabla-datos/tabla-datos.tipos';
import { TarjetaClaseComponent } from '../../../shared/tarjeta-clase/tarjeta-clase.component';

/**
 * Panel principal: las clases de quien entra.
 *
 * Cada rol ve lo suyo sin que esta pantalla filtre nada: el backend ya
 * devuelve las clases que corresponden. Duplicar aquí esa decisión sería
 * mantener la misma regla en dos sitios.
 *
 * Las clases llegan paginadas del servidor, de diez en diez, y la búsqueda por
 * nombre o asignatura también la hace él: filtrar solo lo que hay en la página
 * actual dejaría fuera lo que está en las demás.
 */
@Component({
  selector: 'app-lista-clases',
  standalone: true,
  imports: [
    NgIf, NgFor, RouterLink,
    TarjetaClaseComponent, EstadoVacioComponent, CargandoComponent, TablaDatosComponent, CeldaTablaDirective,
    AvisoComponent, PlazoPipe, PaginadorComponent,
  ],
  templateUrl: './lista-clases.component.html',
  styleUrl: './lista-clases.component.css',
})
export class ListaClasesComponent implements OnInit {

  private readonly clasesService = inject(ClasesService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly router = inject(Router);
  readonly auth = inject(AuthService);


  readonly pagina = signal<Pagina<Clase> | null>(null);
  readonly clases = computed(() => this.pagina()?.contenido ?? []);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  /** Confirmación que deja la página de alta al volver aquí (ver NuevaClaseComponent). */
  readonly aviso = signal<string | null>(history.state?.aviso ?? null);

  // --- gestión (vista de administración) ---
  readonly busqueda = signal('');

  readonly columnas: ColumnaTabla[] = [
    { campo: 'name', titulo: 'Clase' },
    { campo: 'profesores', titulo: 'Profesorado' },
    { campo: 'alumnado', titulo: 'Alumnado' },
    { campo: 'proximaEntrega', titulo: 'Próxima entrega' },
  ];

  /**
   * La búsqueda se hace en el servidor (la tabla espera a que se deje de teclear
   * antes de avisar). Al cambiarla se vuelve a la primera página: la que
   * estábamos viendo podría no existir con menos resultados.
   */
  readonly filtros = computed<FiltroTabla[]>(() => [{
    clave: 'q', tipo: 'busqueda', valor: this.busqueda(),
    placeholder: 'Buscar por nombre o asignatura',
    alCambiar: valor => { this.busqueda.set(valor); this.cargar(0); },
  }]);

  limpiarBusqueda(): void {
    this.busqueda.set('');
    this.cargar(0);
  }

  abrirClase(clase: Clase): void {
    this.router.navigate(['/clases', clase.id]);
  }

  // --- selección en lote ---
  // La selección se vacía al cargar otra página o búsqueda, así que siempre son
  // clases que se están viendo.
  readonly seleccionados = signal<Set<string>>(new Set());
  readonly aplicandoLote = signal(false);

  /** La tabla propone el nuevo conjunto; aquí solo se guarda. */
  seleccionCambia(nuevos: Set<string>): void {
    this.seleccionados.set(nuevos);
  }

  limpiarSeleccion(): void {
    this.seleccionados.set(new Set());
  }

  ngOnInit(): void {
    // El aviso viaja en el historial: se borra para que no reaparezca al recargar
    history.replaceState({ ...history.state, aviso: null }, '');
    this.cargar(0);
  }

  cargar(pagina = 0): void {
    this.cargando.set(true);
    this.error.set(null);
    this.limpiarSeleccion();

    this.clasesService.misClases(pagina, this.busqueda()).subscribe({
      next: (resultado) => {
        // Si al borrar la página en la que estábamos se queda vacía, se retrocede a la última que exista
        if (!resultado.contenido.length && resultado.pagina > 0) {
          this.cargar(resultado.totalPaginas - 1);
          return;
        }
        this.pagina.set(resultado);
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(AvisoComponent.mensajeDe(err, 'No se han podido cargar las clases'));
        this.cargando.set(false);
      },
    });
  }

  /**
   * Borra las clases seleccionadas. Solo se ofrece a administración, y el
   * backend rechaza el borrado si una clase ya tiene entregas o exámenes
   * hechos: aquí solo se pide confirmación, la regla de negocio vive en el
   * servidor. Si alguna del lote falla, se recarga para reflejar solo lo
   * que sí se llegó a borrar, igual que el lote de usuarios.
   */
  async eliminarSeleccionadas(): Promise<void> {
    const ids = Array.from(this.seleccionados());
    if (!ids.length || this.aplicandoLote()) {
      return;
    }

    const confirmado = await this.confirmacion.preguntar(
      `¿Borrar ${ids.length} clase(s)? Se pierde todo lo que tienen dentro: tareas, exámenes, materiales y matriculaciones.`,
      { titulo: 'Borrar clases', textoConfirmar: 'Borrar' });
    if (!confirmado) {
      return;
    }

    this.aplicandoLote.set(true);
    this.error.set(null);

    forkJoin(ids.map(id => this.clasesService.eliminar(id))).subscribe({
      next: () => {
        this.aplicandoLote.set(false);
        this.limpiarSeleccion();
        this.cargar(this.pagina()?.pagina ?? 0);
      },
      error: (err) => {
        this.aplicandoLote.set(false);
        this.error.set(AvisoComponent.mensajeDe(err));
        this.limpiarSeleccion();
        this.cargar(this.pagina()?.pagina ?? 0);
      },
    });
  }

  /** Texto del estado vacío: no es lo mismo no tener clases que no tener ninguna creada. */
  get tituloVacio(): string {
    return this.auth.esAdmin() ? 'Todavía no hay clases' : 'No estás en ninguna clase';
  }

  get descripcionVacia(): string {
    return this.auth.esAdmin()
      ? 'Crea la primera clase del centro y matricula al profesorado y al alumnado.'
      : 'Cuando te matriculen en una clase, aparecerá aquí.';
  }
}
