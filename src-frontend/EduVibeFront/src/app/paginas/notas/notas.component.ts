import { Component, OnInit, inject, signal } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin, map, of, switchMap } from 'rxjs';

import { ClasesService } from '../../core/services/clases.service';
import { Clase, Entrega } from '../../core/models';
import { mediaPonderada } from '../../core/utils/media-ponderada';
import { TAMANO_PAGINA, paginacionLocal, totalDePaginas } from '../../core/utils/paginacion';
import { AvisoComponent } from '../../shared/aviso/aviso.component';
import { CargandoComponent } from '../../shared/cargando/cargando.component';
import { EstadoVacioComponent } from '../../shared/estado-vacio/estado-vacio.component';
import { PaginadorComponent } from '../../shared/paginador/paginador.component';
import { CeldaTablaDirective } from '../../shared/tabla-datos/celda-tabla.directive';
import { TablaDatosComponent } from '../../shared/tabla-datos/tabla-datos.component';
import { ColumnaTabla } from '../../shared/tabla-datos/tabla-datos.tipos';
import { PastillaEstadoComponent } from '../../shared/pastilla-estado/pastilla-estado.component';

/** Las notas de una clase, ya con su media, para pintar una tarjeta por materia. */
interface NotasDeClase {
  clase: Clase;
  entregas: Entrega[];
  media: number | null;
  calificadas: number;
}

/**
 * "Notas": el boletín del alumnado, una tarjeta por cada clase en la que está.
 *
 * No hay un endpoint propio: se reutiliza el de la pestaña de calificaciones
 * de cada clase (misEntregas) y se calcula la media con la misma función, en
 * vez de duplicar la lógica de ponderación en el servidor por una pantalla que
 * solo junta lo que ya existe. Como mucho son unas pocas clases por alumno, así
 * que una petición por clase no pesa.
 *
 * No hay una media global entre clases a propósito: mezclar Matemáticas con
 * Historia en un solo número no significa nada, y cada materia se evalúa por
 * separado.
 *
 * Se pagina en el cliente, de diez en diez, tanto la lista de materias como la
 * tabla de tareas de cada una: hace falta tener todas las entregas cargadas para
 * calcular la media, así que no se piden por páginas al servidor.
 */
@Component({
  selector: 'app-notas',
  standalone: true,
  imports: [
    NgIf, NgFor, RouterLink,
    AvisoComponent, CargandoComponent, EstadoVacioComponent, PastillaEstadoComponent, PaginadorComponent,
    TablaDatosComponent, CeldaTablaDirective,
  ],
  templateUrl: './notas.component.html',
  styleUrl: './notas.component.css',
})
export class NotasComponent implements OnInit {

  readonly columnas: ColumnaTabla[] = [
    { campo: 'assignmentTitle', titulo: 'Tarea' },
    { campo: 'status', titulo: 'Estado' },
    { campo: 'grade', titulo: 'Nota', alinear: 'derecha' },
  ];

  private readonly clasesService = inject(ClasesService);

  readonly materias = signal<NotasDeClase[]>([]);
  readonly paginacionMaterias = paginacionLocal(() => this.materias());

  /** Página de la tabla de tareas de cada materia, por id de clase. */
  private readonly paginasDeEntregas = signal<Record<string, number>>({});
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  totalPaginasDeEntregas(materia: NotasDeClase): number {
    return totalDePaginas(materia.entregas.length);
  }

  paginaDeEntregas(materia: NotasDeClase): number {
    const pedida = this.paginasDeEntregas()[materia.clase.id] ?? 0;
    return Math.min(pedida, this.totalPaginasDeEntregas(materia) - 1);
  }

  entregasVisibles(materia: NotasDeClase): Entrega[] {
    const inicio = this.paginaDeEntregas(materia) * TAMANO_PAGINA;
    return materia.entregas.slice(inicio, inicio + TAMANO_PAGINA);
  }

  irAPaginaDeEntregas(materia: NotasDeClase, pagina: number): void {
    this.paginasDeEntregas.update(paginas => ({ ...paginas, [materia.clase.id]: pagina }));
  }

  ngOnInit(): void {
    this.clasesService.todasMisClases().pipe(
      switchMap(clases => clases.length
        ? forkJoin(clases.map(clase => this.clasesService.misEntregas(clase.id).pipe(
            map((entregas): NotasDeClase => ({
              clase,
              entregas,
              media: mediaPonderada(entregas),
              calificadas: entregas.filter(entrega => entrega.grade !== null).length,
            })))))
        : of([] as NotasDeClase[])),
    ).subscribe({
      next: (materias) => {
        this.materias.set(materias);
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(AvisoComponent.mensajeDe(err, 'No se han podido cargar las notas'));
        this.cargando.set(false);
      },
    });
  }
}
