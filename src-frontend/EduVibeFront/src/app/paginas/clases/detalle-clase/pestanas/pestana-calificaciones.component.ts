import { Component, Input, OnInit, computed, inject, signal } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { RouterLink } from '@angular/router';

import { ClasesService } from '../../../../core/services/clases.service';
import { Entrega } from '../../../../core/models';
import { mediaPonderada } from '../../../../core/utils/media-ponderada';
import { AvisoComponent } from '../../../../shared/aviso/aviso.component';
import { CargandoComponent } from '../../../../shared/cargando/cargando.component';
import { PastillaEstadoComponent } from '../../../../shared/pastilla-estado/pastilla-estado.component';
import { FechaPipe } from '../../../../shared/pipes/fecha.pipe';
import { CeldaTablaDirective } from '../../../../shared/tabla-datos/celda-tabla.directive';
import { TablaDatosComponent } from '../../../../shared/tabla-datos/tabla-datos.component';
import { ColumnaTabla } from '../../../../shared/tabla-datos/tabla-datos.tipos';
import { paginacionLocal } from '../../../../core/utils/paginacion';

/**
 * Pestaña "Calificaciones" del alumnado: sus entregas en esta clase, con la
 * nota de las que ya están corregidas y la media ponderada de esas.
 *
 * La media solo cuenta lo calificado. Incluir en ella lo que aún no se ha
 * corregido daría un número que baja solo porque el profesor va con retraso.
 *
 * Es ponderada y en porcentaje, no la media simple de las notas tal cual: las
 * tareas de la clase pueden valer puntuaciones distintas (100, 50…) y tener
 * un peso distinto en la nota final, así que sumar las notas sin más mezclaría
 * escalas que no son comparables entre sí.
 */
@Component({
  selector: 'app-pestana-calificaciones',
  standalone: true,
  imports: [
    NgIf, NgFor, RouterLink,
    CargandoComponent, PastillaEstadoComponent, AvisoComponent, FechaPipe,
    TablaDatosComponent, CeldaTablaDirective,
  ],
  templateUrl: './pestana-calificaciones.component.html',
  styleUrl: './pestana-calificaciones.component.css',
})
export class PestanaCalificacionesComponent implements OnInit {

  /** Diez por página; la media se calcula con todas las entregas, no solo con las de la página. */
  readonly paginacion = paginacionLocal(() => this.entregas());

  readonly columnas: ColumnaTabla[] = [
    { campo: 'assignmentTitle', titulo: 'Tarea' },
    { campo: 'status', titulo: 'Estado' },
    { campo: 'submittedAt', titulo: 'Entregada' },
    { campo: 'grade', titulo: 'Nota', alinear: 'derecha' },
  ];

  private readonly clasesService = inject(ClasesService);

  @Input({ required: true }) claseId!: string;

  readonly entregas = signal<Entrega[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  readonly calificadas = computed(() => this.entregas().filter(e => e.grade !== null));

  readonly media = computed(() => mediaPonderada(this.entregas()));

  ngOnInit(): void {
    this.cargando.set(true);

    this.clasesService.misEntregas(this.claseId).subscribe({
      next: (entregas) => {
        this.entregas.set(entregas);
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(AvisoComponent.mensajeDe(err, 'No se han podido cargar las calificaciones'));
        this.cargando.set(false);
      },
    });
  }
}
