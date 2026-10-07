import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { AbstractControl, FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ClasesService } from '../../../core/services/clases.service';
import { DetalleClase, PreguntaBanco } from '../../../core/models';
import { AvisoComponent } from '../../../shared/aviso/aviso.component';
import { CargandoComponent } from '../../../shared/cargando/cargando.component';
import { PaginadorComponent } from '../../../shared/paginador/paginador.component';
import { totalDePaginas, trozo } from '../../../core/utils/paginacion';
import { CampoFormularioComponent } from '../../../shared/formulario/campo-formulario.component';
import { PaginaFormularioComponent } from '../../../shared/formulario/pagina-formulario.component';
import { SeccionFormularioComponent } from '../../../shared/formulario/seccion-formulario.component';

type Campo = 'title' | 'durationMinutes';

/**
 * Alta de un examen dentro de una clase. Es una página y no una ventana: un
 * examen se crea completo con sus preguntas y opciones de una sola vez, y en
 * una ventana quedaba demasiado largo y apretado.
 *
 * El formulario es un FormArray de preguntas, cada una con su propio FormArray
 * de opciones. La validación de que cada pregunta tenga exactamente una opción
 * correcta se comprueba aquí para el mensaje inmediato, aunque el backend la
 * exige igual.
 *
 * Carga la clase para saber su nombre, sus unidades/módulos y si quien entra
 * puede editarla; y el banco de preguntas para poder reutilizarlas.
 */
@Component({
  selector: 'app-nuevo-examen',
  standalone: true,
  imports: [
    NgIf, NgFor, ReactiveFormsModule,
    PaginaFormularioComponent, SeccionFormularioComponent, CampoFormularioComponent,
    AvisoComponent, CargandoComponent, PaginadorComponent,
  ],
  templateUrl: './nuevo-examen.component.html',
  styleUrl: './nuevo-examen.component.css',
})
export class NuevoExamenComponent implements OnInit {

  private readonly clasesService = inject(ClasesService);
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly claseId = this.route.snapshot.paramMap.get('id') as string;

  readonly clase = signal<DetalleClase | null>(null);
  readonly cargando = signal(true);
  readonly errorCarga = signal<string | null>(null);

  readonly guardando = signal(false);
  readonly error = signal<string | null>(null);

  // --- banco de preguntas ---
  readonly banco = signal<PreguntaBanco[]>([]);
  /** Página del banco que se está viendo (desde 0). Las marcadas se conservan al cambiar de página. */
  readonly paginaBanco = signal(0);
  readonly bancoVisible = computed(() => trozo(this.banco(), this.paginaBanco()));
  readonly totalPaginasBanco = computed(() => totalDePaginas(this.banco().length));
  /** questionId -> puntos que tendrá en este examen. Solo están aquí las marcadas. */
  readonly seleccionBanco = signal<Map<string, number>>(new Map());

  readonly formulario = this.fb.nonNullable.group({
    title: ['', [Validators.required]],
    description: [''],
    durationMinutes: [30, [Validators.required, Validators.min(1), Validators.max(480)]],
    dueDate: [''],
    topicId: [this.route.snapshot.queryParamMap.get('tema') ?? ''],
    questions: this.fb.array([this.nuevaPregunta()]),
  });

  private readonly mensajes: Record<Campo, { [error: string]: string }> = {
    title: { required: 'Pon un título al examen' },
    durationMinutes: { required: 'Indica la duración', min: 'Mínimo 1 minuto', max: 'Máximo 480 minutos' },
  };

  get preguntas(): FormArray {
    return this.formulario.controls.questions;
  }

  opcionesDe(indicePregunta: number): FormArray {
    return this.preguntas.at(indicePregunta).get('options') as FormArray;
  }

  ngOnInit(): void {
    this.clasesService.detalle(this.claseId).subscribe({
      next: (detalle) => {
        // Sin permiso para editar no hay nada que hacer aquí: se vuelve a la clase
        if (!detalle.puedoEditar) {
          this.router.navigate(['/clases', this.claseId]);
          return;
        }
        this.clase.set(detalle);
        this.cargando.set(false);
      },
      error: (err) => {
        this.errorCarga.set(AvisoComponent.mensajeDe(err, 'No se ha podido cargar la clase'));
        this.cargando.set(false);
      },
    });

    this.clasesService.bancoDePreguntas(this.claseId).subscribe({
      next: (banco) => this.banco.set(banco),
      error: () => this.banco.set([]), // sin banco previo no es un error que deba bloquear el alta
    });
  }

  /** Cómo llama esta clase a sus bloques: según su modo de vista. */
  get etiquetaUnidad(): string {
    return this.clase()?.viewMode === 'flexible' ? 'Módulo' : 'Unidad';
  }

  get etiquetaSinUnidad(): string {
    return 'Sin ' + this.etiquetaUnidad.toLowerCase();
  }

  /** Error de un campo, solo cuando ya se ha tocado o se ha intentado guardar. */
  errorDe(campo: Campo): string | null {
    const control = this.formulario.controls[campo];
    if (!control.touched || !control.errors) {
      return null;
    }
    const clave = Object.keys(control.errors)[0];
    return this.mensajes[campo][clave] ?? 'Valor no válido';
  }

  /** Para marcar en rojo los textos de preguntas y opciones vacíos, una vez tocados. */
  invalido(control: AbstractControl | null): boolean {
    return !!control && control.touched && control.invalid;
  }

  recortarTitulo(): void {
    const control = this.formulario.controls.title;
    control.setValue(control.value.trim());
  }

  recortarDescripcion(): void {
    const control = this.formulario.controls.description;
    control.setValue(control.value.trim());
  }

  // ------------------------------------------------------------------ preguntas

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

  // ------------------------------------------------------------------ banco

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

  // ------------------------------------------------------------------ guardar

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

  guardar(): void {
    this.formulario.markAllAsTouched();

    if (this.formulario.invalid || this.guardando()) {
      return;
    }

    const reuseQuestions = Array.from(this.seleccionBanco().entries())
      .map(([questionId, points]) => ({ questionId, points }));

    if (this.preguntas.length === 0 && reuseQuestions.length === 0) {
      this.error.set('El examen necesita al menos una pregunta, nueva o del banco');
      return;
    }

    const errorPreguntas = this.validarPreguntas();
    if (errorPreguntas) {
      this.error.set(errorPreguntas);
      return;
    }

    this.guardando.set(true);
    this.error.set(null);

    const { title, description, durationMinutes, dueDate, topicId, questions } = this.formulario.getRawValue();

    this.clasesService.crearExamen(this.claseId, {
      title,
      description: description.trim() || undefined,
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
      next: () => this.router.navigate(['/clases', this.claseId]),
      error: (err) => {
        this.guardando.set(false);
        this.error.set(AvisoComponent.mensajeDe(err));
      },
    });
  }
}
