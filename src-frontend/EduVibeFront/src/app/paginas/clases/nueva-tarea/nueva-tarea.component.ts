import { Component, OnInit, inject, signal } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ClasesService } from '../../../core/services/clases.service';
import { DetalleClase } from '../../../core/models';
import { AvisoComponent } from '../../../shared/aviso/aviso.component';
import { CargandoComponent } from '../../../shared/cargando/cargando.component';
import { CampoFormularioComponent } from '../../../shared/formulario/campo-formulario.component';
import { PaginaFormularioComponent } from '../../../shared/formulario/pagina-formulario.component';
import { SeccionFormularioComponent } from '../../../shared/formulario/seccion-formulario.component';

type Campo = 'title' | 'points' | 'latePenaltyPercent' | 'weight';

/**
 * Alta de una tarea dentro de una clase. Es una página y no una ventana: son
 * ocho campos de tres tipos distintos (enunciado, puntuación, modo de entrega)
 * y en una ventana de 480 px quedaban apretados.
 *
 * Carga la clase para saber su nombre, cómo llama a sus bloques (unidades o
 * módulos) y si quien entra puede editarla; el backend vuelve a comprobarlo
 * al guardar, esto solo evita enseñar un formulario que no se va a poder usar.
 */
@Component({
  selector: 'app-nueva-tarea',
  standalone: true,
  imports: [
    NgIf, NgFor, ReactiveFormsModule,
    PaginaFormularioComponent, SeccionFormularioComponent, CampoFormularioComponent,
    AvisoComponent, CargandoComponent,
  ],
  templateUrl: './nueva-tarea.component.html',
})
export class NuevaTareaComponent implements OnInit {

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

  readonly formulario = this.fb.nonNullable.group({
    title: ['', [Validators.required]],
    description: [''],
    dueDate: [''],
    points: [100, [Validators.required, Validators.min(1), Validators.max(1000)]],
    latePenaltyPercent: [0, [Validators.min(0), Validators.max(100)]],
    weight: [1, [Validators.required, Validators.min(0.01)]],
    groupAssignment: [false],
    topicId: [this.route.snapshot.queryParamMap.get('tema') ?? ''],
  });

  private readonly mensajes: Record<Campo, { [error: string]: string }> = {
    title: { required: 'Pon un título a la tarea' },
    points: { required: 'Indica los puntos', min: 'Mínimo 1 punto', max: 'Máximo 1000 puntos' },
    latePenaltyPercent: { min: 'No puede ser negativa', max: 'Máximo 100 %' },
    weight: { required: 'Indica el peso', min: 'Tiene que ser mayor que 0' },
  };

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

  recortarTitulo(): void {
    const control = this.formulario.controls.title;
    control.setValue(control.value.trim());
  }

  guardar(): void {
    this.formulario.markAllAsTouched();

    if (this.formulario.invalid || this.guardando()) {
      return;
    }

    this.guardando.set(true);
    this.error.set(null);

    const {
      title, description, dueDate, points, latePenaltyPercent, weight, groupAssignment, topicId,
    } = this.formulario.getRawValue();

    this.clasesService.crearTarea(this.claseId, {
      title,
      description: description.trim() || undefined,
      // El input datetime-local da "2026-10-02T18:30"; la API espera un
      // instante en UTC, y eso es justo lo que hace toISOString()
      dueDate: dueDate ? new Date(dueDate).toISOString() : null,
      points,
      latePenaltyPercent,
      weight,
      groupAssignment,
      topicId: topicId || null,
    }).subscribe({
      next: () => this.router.navigate(['/clases', this.claseId]),
      error: (err) => {
        this.guardando.set(false);
        this.error.set(AvisoComponent.mensajeDe(err));
      },
    });
  }
}
