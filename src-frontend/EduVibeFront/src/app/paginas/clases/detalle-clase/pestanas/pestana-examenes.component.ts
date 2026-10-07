import { Component, Input, OnChanges, OnInit, computed, inject, signal } from '@angular/core';
import { NgIf } from '@angular/common';
import { RouterLink } from '@angular/router';

import { ClasesService } from '../../../../core/services/clases.service';
import { Examen, Tema } from '../../../../core/models';
import { AvisoComponent } from '../../../../shared/aviso/aviso.component';
import { CargandoComponent } from '../../../../shared/cargando/cargando.component';
import { EstadoVacioComponent } from '../../../../shared/estado-vacio/estado-vacio.component';
import { PastillaEstadoComponent } from '../../../../shared/pastilla-estado/pastilla-estado.component';
import { FechaPipe, PlazoPipe } from '../../../../shared/pipes/fecha.pipe';
import { CeldaTablaDirective } from '../../../../shared/tabla-datos/celda-tabla.directive';
import { TablaDatosComponent } from '../../../../shared/tabla-datos/tabla-datos.component';
import { ColumnaTabla } from '../../../../shared/tabla-datos/tabla-datos.tipos';
import { comparar, listaLocal } from '../../../../core/utils/lista-local';

/** Valor del filtro de unidad que significa "los exámenes sin unidad". */
const SIN_UNIDAD = '__sin-unidad';

/** Un examen ya emparejado con el título de la unidad a la que pertenece. */
interface FilaExamen extends Examen {
  temaTitulo: string;
}

/**
 * Pestaña "Exámenes": los exámenes tipo test de la clase en una tabla, con la unidad como columna y como filtro.
 *
 * El alta no se hace aquí: el botón "Nuevo examen" lleva a la página
 * /clases/:id/examenes/nuevo (NuevoExamenComponent).
 */
@Component({
  selector: 'app-pestana-examenes',
  standalone: true,
  imports: [
    NgIf, RouterLink,
    CargandoComponent, EstadoVacioComponent, PastillaEstadoComponent,
    AvisoComponent, FechaPipe, PlazoPipe,
    TablaDatosComponent, CeldaTablaDirective,
  ],
  templateUrl: './pestana-examenes.component.html',
})
export class PestanaExamenesComponent implements OnInit, OnChanges {

  private readonly clasesService = inject(ClasesService);

  @Input({ required: true }) claseId!: string;
  @Input() puedoEditar = false;
  @Input() temas: Tema[] = [];

  /**
   * Filtra a un solo bloque: `undefined` = todos (comportamiento por defecto),
   * un id de tema = solo ese, `null` = solo lo que no tiene tema.
   */
  @Input() temaFiltro: string | null | undefined = undefined;

  /** Cómo llamar al agrupador en este modo de vista: "Unidad" o "Módulo". */
  @Input() etiquetaUnidad = 'Unidad';

  /**
   * Los @Input como señal, para que filas, columnas y filtros se recalculen
   * solos cuando el padre cambia de unidad o llegan las unidades.
   */
  private readonly contexto = signal({
    temas: [] as Tema[], temaFiltro: undefined as string | null | undefined, puedoEditar: false, etiquetaUnidad: 'Unidad',
  });

  ngOnChanges(): void {
    this.contexto.set({
      temas: this.temas, temaFiltro: this.temaFiltro, puedoEditar: this.puedoEditar, etiquetaUnidad: this.etiquetaUnidad,
    });
    this.lista.reiniciar();
  }

  readonly examenes = signal<Examen[]>([]);

  /** Los exámenes de la unidad elegida arriba ({@link temaFiltro}), con el título de su unidad. */
  private readonly filas = computed<FilaExamen[]>(() => {
    const { temas, temaFiltro } = this.contexto();
    return this.examenes()
      .filter(examen => temaFiltro === undefined || (temaFiltro === null ? !examen.topicId : examen.topicId === temaFiltro))
      .map(examen => ({
        ...examen,
        temaTitulo: temas.find(tema => tema.id === examen.topicId)?.title ?? this.etiquetaSinUnidad,
      }));
  });

  /** Con una unidad ya elegida arriba, la columna y el filtro de unidad sobran. */
  private readonly mostrarUnidad = () => this.contexto().temaFiltro === undefined && this.contexto().temas.length > 0;

  readonly lista = listaLocal(() => this.filas(), {
    placeholderBusqueda: 'Buscar examen',
    textos: examen => [examen.title],
    selectores: {
      unidad: {
        placeholder: 'Todas',
        visible: this.mostrarUnidad,
        opciones: () => [
          ...this.contexto().temas.map(tema => ({ valor: tema.id, etiqueta: tema.title })),
          { valor: SIN_UNIDAD, etiqueta: this.etiquetaSinUnidad },
        ],
        encaja: (examen, valor) => valor === SIN_UNIDAD ? !examen.topicId : examen.topicId === valor,
      },
      estado: {
        placeholder: 'Cualquier estado',
        visible: () => !this.contexto().puedoEditar,
        opciones: () => [
          { valor: 'no_empezado', etiqueta: 'Sin empezar' },
          { valor: 'en_curso', etiqueta: 'En curso' },
          { valor: 'entregado', etiqueta: 'Entregado' },
        ],
        encaja: (examen, valor) => this.estadoDe(examen) === valor,
      },
    },
    comparadores: {
      title: (a, b) => comparar(a.title, b.title),
      temaTitulo: (a, b) => comparar(a.temaTitulo, b.temaTitulo),
      dueDate: (a, b) => comparar(a.dueDate, b.dueDate),
      durationMinutes: (a, b) => comparar(a.durationMinutes, b.durationMinutes),
      numeroPreguntas: (a, b) => comparar(a.numeroPreguntas, b.numeroPreguntas),
      estado: (a, b) => comparar(a.intentosRecibidos ?? a.miNota ?? this.estadoDe(a), b.intentosRecibidos ?? b.miNota ?? this.estadoDe(b)),
    },
  });

  readonly columnas = computed<ColumnaTabla[]>(() => [
    { campo: 'title', titulo: 'Examen', ordenable: true },
    ...(this.mostrarUnidad() ? [{ campo: 'temaTitulo', titulo: this.contexto().etiquetaUnidad, ordenable: true }] : []),
    { campo: 'dueDate', titulo: 'Fecha límite', ordenable: true },
    { campo: 'durationMinutes', titulo: 'Duración', alinear: 'derecha' as const, ordenable: true },
    { campo: 'numeroPreguntas', titulo: 'Preguntas', alinear: 'derecha' as const, ordenable: true },
    { campo: 'estado', titulo: this.contexto().puedoEditar ? 'Intentos' : 'Estado', ordenable: true },
  ]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);

    this.clasesService.examenes(this.claseId).subscribe({
      next: (examenes) => {
        this.examenes.set(examenes);
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(AvisoComponent.mensajeDe(err, 'No se han podido cargar los exámenes'));
        this.cargando.set(false);
      },
    });
  }

  get etiquetaUnidadMinuscula(): string {
    return this.etiquetaUnidad.toLowerCase();
  }

  get etiquetaSinUnidad(): string {
    return 'Sin ' + this.etiquetaUnidadMinuscula;
  }

  /** Estado que se muestra al alumnado: sin intento todavía cuenta como "no_empezado". */
  estadoDe(examen: Examen): 'no_empezado' | 'en_curso' | 'entregado' {
    return examen.miEstado ?? 'no_empezado';
  }
}
