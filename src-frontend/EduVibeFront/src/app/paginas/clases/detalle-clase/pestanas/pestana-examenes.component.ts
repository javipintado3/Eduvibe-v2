import { Component, Input, OnChanges, OnInit, computed, inject, signal } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ClasesService } from '../../../../core/services/clases.service';
import { Examen, PreguntaBanco, Tema } from '../../../../core/models';
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

/** Valor del filtro de unidad que significa "los exámenes sin unidad". */
const SIN_UNIDAD = '__sin-unidad';

/** Un examen ya emparejado con el título de la unidad a la que pertenece. */
interface FilaExamen extends Examen {
  temaTitulo: string;
}

/**
 * Pestaña "Exámenes": los exámenes tipo test de la clase en una tabla, con la unidad como columna y como filtro.
 *
 * El alta es la parte más grande: un examen se crea completo con sus
 * preguntas y opciones de una sola vez, así que el formulario es un FormArray
 * de preguntas, cada una con su propio FormArray de opciones. La validación
 * de que cada pregunta tenga exactamente una opción correcta se comprueba
 * aquí para el mensaje inmediato, aunque el backend la exige igual.
 */
@Component({
  selector: 'app-pestana-examenes',
  standalone: true,
  imports: [
    NgIf, NgFor, RouterLink, ReactiveFormsModule,
    CargandoComponent, EstadoVacioComponent, PastillaEstadoComponent,
    DialogoComponent, AvisoComponent, FechaPipe, PlazoPipe,
    TablaDatosComponent, CeldaTablaDirective,
  ],
  templateUrl: './pestana-examenes.component.html',
  styleUrl: './pestana-examenes.component.css',
})
export class PestanaExamenesComponent implements OnInit, OnChanges {

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

  readonly dialogoAbierto = signal(false);
  readonly creando = signal(false);
  readonly errorFormulario = signal<string | null>(null);

  // --- banco de preguntas ---
  readonly banco = signal<PreguntaBanco[]>([]);
  /** questionId -> puntos que tendrá en este examen. Solo están aquí las marcadas. */
  readonly seleccionBanco = signal<Map<string, number>>(new Map());

  readonly formulario = this.fb.nonNullable.group({
    title: ['', [Validators.required]],
    description: [''],
    durationMinutes: [30, [Validators.required, Validators.min(1), Validators.max(480)]],
    dueDate: [''],
    topicId: [''],
    questions: this.fb.array([this.nuevaPregunta()]),
  });

  get preguntas(): FormArray {
    return this.formulario.controls.questions;
  }

  opcionesDe(indicePregunta: number): FormArray {
    return this.preguntas.at(indicePregunta).get('options') as FormArray;
  }

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

  // ------------------------------------------------------------------ alta

  private nuevaPregunta() {
    return this.fb.nonNullable.group({
      text: ['', [Validators.required]],
      points: [1, [Validators.required, Validators.min(1)]],
      options: this.fb.array([this.nuevaOpcion(), this.nuevaOpcion()]),
    });
  }

  private nuevaOpcion() {
    return this.fb.nonNullable.group({
      text: ['', [Validators.required]],
      correct: [false],
    });
  }

  anadirPregunta(): void {
    this.preguntas.push(this.nuevaPregunta());
  }

  /**
   * A diferencia de una pregunta de tarea (que siempre necesita al menos
   * una), aquí sí se puede quitar la última: el examen puede armarse entero
   * reutilizando preguntas del banco, sin escribir ninguna nueva.
   */
  quitarPregunta(indice: number): void {
    this.preguntas.removeAt(indice);
  }

  anadirOpcion(indicePregunta: number): void {
    this.opcionesDe(indicePregunta).push(this.nuevaOpcion());
  }

  quitarOpcion(indicePregunta: number, indiceOpcion: number): void {
    const opciones = this.opcionesDe(indicePregunta);
    if (opciones.length > 2) {
      opciones.removeAt(indiceOpcion);
    }
  }

  /**
   * Deja marcada como correcta solo la opción elegida dentro de su pregunta:
   * son radios, no checkboxes, así que no puede haber dos correctas a la vez.
   */
  marcarCorrecta(indicePregunta: number, indiceOpcion: number): void {
    const opciones = this.opcionesDe(indicePregunta);
    opciones.controls.forEach((opcion, i) => opcion.get('correct')!.setValue(i === indiceOpcion));
  }

  abrirDialogo(): void {
    this.formulario.reset({
      title: '', description: '', durationMinutes: 30, dueDate: '', topicId: this.temaFiltro ?? '',
    });
    while (this.preguntas.length) {
      this.preguntas.removeAt(0);
    }
    this.preguntas.push(this.nuevaPregunta());
    this.seleccionBanco.set(new Map());
    this.errorFormulario.set(null);
    this.dialogoAbierto.set(true);

    this.clasesService.bancoDePreguntas(this.claseId).subscribe({
      next: (banco) => this.banco.set(banco),
      error: () => this.banco.set([]), // sin banco previo no es un error que deba bloquear el alta
    });
  }

  /** Si la pregunta ya está elegida del banco, o no. */
  estaEnBanco(preguntaId: string): boolean {
    return this.seleccionBanco().has(preguntaId);
  }

  alternarDelBanco(pregunta: PreguntaBanco, marcado: boolean): void {
    this.seleccionBanco.update(mapa => {
      const nuevo = new Map(mapa);
      if (marcado) {
        nuevo.set(pregunta.id, pregunta.points);
      } else {
        nuevo.delete(pregunta.id);
      }
      return nuevo;
    });
  }

  actualizarPuntosDelBanco(preguntaId: string, points: number): void {
    if (this.seleccionBanco().has(preguntaId)) {
      this.seleccionBanco.update(mapa => new Map(mapa).set(preguntaId, points));
    }
  }

  /** Cada pregunta necesita texto, al menos dos opciones con texto, y exactamente una correcta. */
  private validarPreguntas(): string | null {
    const valores = this.preguntas.getRawValue() as {
      text: string; options: { text: string; correct: boolean }[];
    }[];

    for (let i = 0; i < valores.length; i++) {
      const pregunta = valores[i];
      const conTexto = pregunta.options.filter(o => o.text.trim());
      if (conTexto.length < 2) {
        return `La pregunta ${i + 1} necesita al menos dos opciones con texto`;
      }
      const correctas = pregunta.options.filter(o => o.correct).length;
      if (correctas !== 1) {
        return `La pregunta ${i + 1} necesita exactamente una opción correcta`;
      }
    }
    return null;
  }

  crear(): void {
    this.formulario.markAllAsTouched();

    if (this.formulario.invalid || this.creando()) {
      return;
    }

    const reuseQuestions = Array.from(this.seleccionBanco().entries())
      .map(([questionId, points]) => ({ questionId, points }));

    if (this.preguntas.length === 0 && reuseQuestions.length === 0) {
      this.errorFormulario.set('El examen necesita al menos una pregunta, nueva o del banco');
      return;
    }

    const errorPreguntas = this.validarPreguntas();
    if (errorPreguntas) {
      this.errorFormulario.set(errorPreguntas);
      return;
    }

    this.creando.set(true);
    this.errorFormulario.set(null);

    const { title, description, durationMinutes, dueDate, topicId, questions } = this.formulario.getRawValue();

    this.clasesService.crearExamen(this.claseId, {
      title,
      description: description || undefined,
      durationMinutes,
      // El input datetime-local da "2026-10-02T18:30"; la API espera un
      // instante en UTC, y eso es justo lo que hace toISOString()
      dueDate: dueDate ? new Date(dueDate).toISOString() : null,
      topicId: topicId || null,
      questions: questions.map(p => ({
        text: p.text,
        points: p.points,
        options: p.options.filter(o => o.text.trim()).map(o => ({ text: o.text, correct: o.correct })),
      })),
      reuseQuestions,
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
