import { Component, Input, OnChanges, OnInit, computed, inject, signal } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ClasesService } from '../../../../core/services/clases.service';
import { Tarea, Tema } from '../../../../core/models';
import { AvisoComponent } from '../../../../shared/aviso/aviso.component';
import { CargandoComponent } from '../../../../shared/cargando/cargando.component';
import { DialogoComponent } from '../../../../shared/dialogo/dialogo.component';
import { EstadoVacioComponent } from '../../../../shared/estado-vacio/estado-vacio.component';
import { PastillaEstadoComponent } from '../../../../shared/pastilla-estado/pastilla-estado.component';
import { FechaPipe, PlazoPipe } from '../../../../shared/pipes/fecha.pipe';
import { CeldaTablaDirective } from '../../../../shared/tabla-datos/celda-tabla.directive';
import { TablaDatosComponent } from '../../../../shared/tabla-datos/tabla-datos.component';
import { ColumnaTabla } from '../../../../shared/tabla-datos/tabla-datos.tipos';
import { comparar, listaLocal } from '../../../../core/utils/lista-local';

/** Valor del filtro de unidad que significa "las tareas sin unidad". */
const SIN_UNIDAD = '__sin-unidad';

/** Una tarea ya emparejada con el título de la unidad a la que pertenece. */
interface FilaTarea extends Tarea {
  temaTitulo: string;
}

/**
 * Pestaña "Trabajo de clase": las tareas en una tabla, con la unidad como columna y como filtro.
 *
 * Cada fila muestra lo que le interesa a quien mira: al alumnado, el estado de
 * su entrega; al profesorado, cuántas lleva recibidas. Esa distinción la hace
 * la API, y aquí solo se pinta el campo que venga relleno.
 */
@Component({
  selector: 'app-pestana-trabajo',
  standalone: true,
  imports: [
    NgIf, NgFor, RouterLink, ReactiveFormsModule,
    CargandoComponent, EstadoVacioComponent, PastillaEstadoComponent,
    DialogoComponent, AvisoComponent, FechaPipe, PlazoPipe,
    TablaDatosComponent, CeldaTablaDirective,
  ],
  templateUrl: './pestana-trabajo.component.html',
  styleUrl: './pestana-trabajo.component.css',
})
export class PestanaTrabajoComponent implements OnInit, OnChanges {

  private readonly clasesService = inject(ClasesService);
  private readonly fb = inject(FormBuilder);

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

  readonly tareas = signal<Tarea[]>([]);

  /** Las tareas de la unidad elegida arriba ({@link temaFiltro}), con el título de su unidad. */
  private readonly filas = computed<FilaTarea[]>(() => {
    const { temas, temaFiltro } = this.contexto();
    return this.tareas()
      .filter(tarea => temaFiltro === undefined || (temaFiltro === null ? !tarea.topicId : tarea.topicId === temaFiltro))
      .map(tarea => ({
        ...tarea,
        temaTitulo: temas.find(tema => tema.id === tarea.topicId)?.title ?? this.etiquetaSinUnidad,
      }));
  });

  /** Con una unidad ya elegida arriba, la columna y el filtro de unidad sobran. */
  private readonly mostrarUnidad = () => this.contexto().temaFiltro === undefined && this.contexto().temas.length > 0;

  readonly lista = listaLocal(() => this.filas(), {
    placeholderBusqueda: 'Buscar tarea',
    textos: tarea => [tarea.title],
    selectores: {
      unidad: {
        placeholder: 'Todas',
        visible: this.mostrarUnidad,
        opciones: () => [
          ...this.contexto().temas.map(tema => ({ valor: tema.id, etiqueta: tema.title })),
          { valor: SIN_UNIDAD, etiqueta: this.etiquetaSinUnidad },
        ],
        encaja: (tarea, valor) => valor === SIN_UNIDAD ? !tarea.topicId : tarea.topicId === valor,
      },
      estado: {
        placeholder: 'Cualquier estado',
        visible: () => !this.contexto().puedoEditar,
        opciones: () => [
          { valor: 'sin-empezar', etiqueta: 'Sin empezar' },
          { valor: 'draft', etiqueta: 'Borrador' },
          { valor: 'submitted', etiqueta: 'Entregada' },
          { valor: 'graded', etiqueta: 'Calificada' },
        ],
        encaja: (tarea, valor) => this.estadoDe(tarea) === valor,
      },
    },
    comparadores: {
      title: (a, b) => comparar(a.title, b.title),
      temaTitulo: (a, b) => comparar(a.temaTitulo, b.temaTitulo),
      dueDate: (a, b) => comparar(a.dueDate, b.dueDate),
      points: (a, b) => comparar(a.points, b.points),
      estado: (a, b) => comparar(a.entregasRecibidas ?? this.estadoDe(a), b.entregasRecibidas ?? this.estadoDe(b)),
    },
  });

  readonly columnas = computed<ColumnaTabla[]>(() => [
    { campo: 'title', titulo: 'Tarea', ordenable: true },
    ...(this.mostrarUnidad() ? [{ campo: 'temaTitulo', titulo: this.contexto().etiquetaUnidad, ordenable: true }] : []),
    { campo: 'dueDate', titulo: 'Fecha límite', ordenable: true },
    { campo: 'points', titulo: 'Puntos', alinear: 'derecha' as const, ordenable: true },
    { campo: 'estado', titulo: this.contexto().puedoEditar ? 'Entregas' : 'Estado', ordenable: true },
  ]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  readonly dialogoAbierto = signal(false);
  readonly creando = signal(false);
  readonly errorFormulario = signal<string | null>(null);

  readonly formulario = this.fb.nonNullable.group({
    title: ['', [Validators.required]],
    description: [''],
    dueDate: [''],
    points: [100, [Validators.required, Validators.min(1)]],
    latePenaltyPercent: [0, [Validators.min(0), Validators.max(100)]],
    weight: [1, [Validators.required, Validators.min(0.01)]],
    groupAssignment: [false],
    topicId: [''],
  });

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);

    this.clasesService.tareas(this.claseId).subscribe({
      next: (tareas) => {
        this.tareas.set(tareas);
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(AvisoComponent.mensajeDe(err, 'No se han podido cargar las tareas'));
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

  /** Estado que se muestra al alumnado: sin entrega todavía cuenta como "sin empezar". */
  estadoDe(tarea: Tarea): 'draft' | 'submitted' | 'graded' | 'sin-empezar' {
    return tarea.miEstado ?? 'sin-empezar';
  }

  abrirDialogo(): void {
    this.formulario.reset({
      title: '', description: '', dueDate: '', points: 100, latePenaltyPercent: 0, weight: 1,
      groupAssignment: false, topicId: this.temaFiltro ?? '',
    });
    this.errorFormulario.set(null);
    this.dialogoAbierto.set(true);
  }

  crear(): void {
    this.formulario.markAllAsTouched();

    if (this.formulario.invalid || this.creando()) {
      return;
    }

    this.creando.set(true);
    this.errorFormulario.set(null);

    const {
      title, description, dueDate, points, latePenaltyPercent, weight, groupAssignment, topicId,
    } = this.formulario.getRawValue();

    this.clasesService.crearTarea(this.claseId, {
      title,
      description: description || undefined,
      // El input datetime-local da "2026-10-02T18:30"; la API espera un
      // instante en UTC, y eso es justo lo que hace toISOString()
      dueDate: dueDate ? new Date(dueDate).toISOString() : null,
      points,
      latePenaltyPercent,
      weight,
      groupAssignment,
      topicId: topicId || null,
    }).subscribe({
      next: () => {
        this.creando.set(false);
        this.dialogoAbierto.set(false);
        this.cargar();
      },
      error: (err) => {
        this.creando.set(false);
        this.errorFormulario.set(AvisoComponent.mensajeDe(err));
      },
    });
  }
}
