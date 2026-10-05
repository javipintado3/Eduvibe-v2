import { Component, Input, OnChanges, OnInit, computed, inject, signal } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ClasesService } from '../../../../core/services/clases.service';
import { HiloForo, Tema } from '../../../../core/models';
import { AvisoComponent } from '../../../../shared/aviso/aviso.component';
import { CargandoComponent } from '../../../../shared/cargando/cargando.component';
import { DialogoComponent } from '../../../../shared/dialogo/dialogo.component';
import { EstadoVacioComponent } from '../../../../shared/estado-vacio/estado-vacio.component';
import { FechaPipe } from '../../../../shared/pipes/fecha.pipe';
import { CeldaTablaDirective } from '../../../../shared/tabla-datos/celda-tabla.directive';
import { TablaDatosComponent } from '../../../../shared/tabla-datos/tabla-datos.component';
import { ColumnaTabla } from '../../../../shared/tabla-datos/tabla-datos.tipos';
import { comparar, listaLocal } from '../../../../core/utils/lista-local';

/**
 * Pestaña "Foro": los hilos de debate de la clase, con más actividad
 * reciente primero.
 *
 * Cualquier persona matriculada puede abrir un hilo, no solo el
 * profesorado: el foro es un espacio de la clase entera. Al crearlo se
 * navega directamente a su detalle, porque un hilo recién abierto sin
 * mensaje no tendría sentido y el primer mensaje ya se manda en el mismo gesto.
 */
/** Valor del filtro de unidad que significa "los hilos sin unidad". */
const SIN_UNIDAD = '__sin-unidad';

@Component({
  selector: 'app-pestana-foro',
  standalone: true,
  imports: [
    NgIf, NgFor, RouterLink, ReactiveFormsModule,
    CargandoComponent, EstadoVacioComponent, DialogoComponent, AvisoComponent, FechaPipe,
    TablaDatosComponent, CeldaTablaDirective,
  ],
  templateUrl: './pestana-foro.component.html',
})
export class PestanaForoComponent implements OnInit, OnChanges {

  private readonly clasesService = inject(ClasesService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  @Input({ required: true }) claseId!: string;
  @Input() temas: Tema[] = [];

  /**
   * Filtra a un solo tema: `undefined` = todos (comportamiento por defecto),
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
    temas: [] as Tema[], temaFiltro: undefined as string | null | undefined, etiquetaUnidad: 'Unidad',
  });

  ngOnChanges(): void {
    this.contexto.set({ temas: this.temas, temaFiltro: this.temaFiltro, etiquetaUnidad: this.etiquetaUnidad });
    this.lista.reiniciar();
  }

  readonly hilos = signal<HiloForo[]>([]);

  /** Los hilos de la unidad elegida arriba ({@link temaFiltro}), con el título de su unidad. */
  private readonly filas = computed(() => {
    const { temaFiltro } = this.contexto();
    return this.hilos()
      .filter(hilo => temaFiltro === undefined || hilo.topicId === temaFiltro)
      .map(hilo => ({ ...hilo, temaTitulo: this.tituloDelTema(hilo.topicId) }));
  });

  /** Con una unidad ya elegida arriba, la columna y el filtro de unidad sobran. */
  private readonly mostrarUnidad = () => this.contexto().temaFiltro === undefined && this.contexto().temas.length > 0;

  readonly lista = listaLocal(() => this.filas(), {
    placeholderBusqueda: 'Buscar hilo o autor',
    textos: hilo => [hilo.title, hilo.authorName],
    selectores: {
      unidad: {
        placeholder: 'Todas',
        visible: this.mostrarUnidad,
        opciones: () => [
          ...this.contexto().temas.map(tema => ({ valor: tema.id, etiqueta: tema.title })),
          { valor: SIN_UNIDAD, etiqueta: this.etiquetaSinUnidad },
        ],
        encaja: (hilo, valor) => valor === SIN_UNIDAD ? !hilo.topicId : hilo.topicId === valor,
      },
    },
    comparadores: {
      title: (a, b) => comparar(a.title, b.title),
      temaTitulo: (a, b) => comparar(a.temaTitulo, b.temaTitulo),
      authorName: (a, b) => comparar(a.authorName, b.authorName),
      postCount: (a, b) => comparar(a.postCount, b.postCount),
      lastActivityAt: (a, b) => comparar(a.lastActivityAt, b.lastActivityAt),
    },
  });

  readonly columnas = computed<ColumnaTabla[]>(() => [
    { campo: 'title', titulo: 'Hilo', ordenable: true },
    ...(this.mostrarUnidad() ? [{ campo: 'temaTitulo', titulo: this.contexto().etiquetaUnidad, ordenable: true }] : []),
    { campo: 'authorName', titulo: 'Autor', ordenable: true },
    { campo: 'postCount', titulo: 'Mensajes', alinear: 'derecha' as const, ordenable: true },
    { campo: 'lastActivityAt', titulo: 'Última actividad', ordenable: true },
  ]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  readonly dialogoAbierto = signal(false);
  readonly abriendo = signal(false);
  readonly errorFormulario = signal<string | null>(null);

  readonly formulario = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(200)]],
    content: ['', [Validators.required]],
    topicId: [''],
  });

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);

    this.clasesService.hilosDeForo(this.claseId).subscribe({
      next: (hilos) => {
        this.hilos.set(hilos);
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(AvisoComponent.mensajeDe(err, 'No se han podido cargar los hilos'));
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

  tituloDelTema(topicId: string | null): string {
    if (!topicId) {
      return this.etiquetaSinUnidad;
    }
    return this.temas.find(t => t.id === topicId)?.title ?? this.etiquetaSinUnidad;
  }

  abrirDialogo(): void {
    this.formulario.reset({ title: '', content: '', topicId: this.temaFiltro ?? '' });
    this.errorFormulario.set(null);
    this.dialogoAbierto.set(true);
  }

  crear(): void {
    this.formulario.markAllAsTouched();

    if (this.formulario.invalid || this.abriendo()) {
      return;
    }

    this.abriendo.set(true);
    this.errorFormulario.set(null);

    const { title, content, topicId } = this.formulario.getRawValue();

    this.clasesService.abrirHilo(this.claseId, { title, content, topicId: topicId || null }).subscribe({
      next: (hilo) => {
        this.abriendo.set(false);
        this.dialogoAbierto.set(false);
        this.router.navigate(['/foro', hilo.id]);
      },
      error: (err) => {
        this.abriendo.set(false);
        this.errorFormulario.set(AvisoComponent.mensajeDe(err));
      },
    });
  }
}
