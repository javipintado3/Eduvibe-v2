import { Component, Input, OnChanges, OnInit, computed, inject, signal } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ClasesService } from '../../../../core/services/clases.service';
import { ConfirmacionService } from '../../../../core/services/confirmacion.service';
import { MaterialesService } from '../../../../core/services/materiales.service';
import { Material, Tema, TipoMaterial } from '../../../../core/models';
import { AvisoComponent } from '../../../../shared/aviso/aviso.component';
import { CargandoComponent } from '../../../../shared/cargando/cargando.component';
import { DialogoComponent } from '../../../../shared/dialogo/dialogo.component';
import { PastillaEstadoComponent } from '../../../../shared/pastilla-estado/pastilla-estado.component';
import { FechaPipe } from '../../../../shared/pipes/fecha.pipe';
import { RutaArchivoPipe } from '../../../../shared/pipes/ruta-archivo.pipe';
import { SubidaArchivoComponent } from '../../../../shared/subida-archivo/subida-archivo.component';
import { CeldaTablaDirective } from '../../../../shared/tabla-datos/celda-tabla.directive';
import { TablaDatosComponent } from '../../../../shared/tabla-datos/tabla-datos.component';
import { ColumnaTabla } from '../../../../shared/tabla-datos/tabla-datos.tipos';
import { comparar, listaLocal } from '../../../../core/utils/lista-local';

/**
 * Pestaña "Materiales": apuntes, enlaces y vídeos de la clase.
 *
 * El mismo diálogo sirve para crear y para editar —como en la mayoría de
 * pantallas de la aplicación—, distinguido por `editando`: null es alta,
 * un material es edición.
 */
/** Valor del filtro de unidad que significa "los materiales sin unidad". */
const SIN_UNIDAD = '__sin-unidad';

/** Un material ya emparejado con el título de la unidad a la que pertenece. */
interface FilaMaterial extends Material {
  temaTitulo: string;
}

@Component({
  selector: 'app-pestana-materiales',
  standalone: true,
  imports: [
    NgIf, NgFor, ReactiveFormsModule,
    CargandoComponent, DialogoComponent, AvisoComponent, PastillaEstadoComponent,
    SubidaArchivoComponent, RutaArchivoPipe, FechaPipe,
    TablaDatosComponent, CeldaTablaDirective,
  ],
  templateUrl: './pestana-materiales.component.html',
  styleUrl: './pestana-materiales.component.css',
})
export class PestanaMaterialesComponent implements OnInit, OnChanges {

  private readonly clasesService = inject(ClasesService);
  private readonly materialesService = inject(MaterialesService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly fb = inject(FormBuilder);

  @Input({ required: true }) claseId!: string;
  @Input() puedoEditar = false;
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
    temas: [] as Tema[], temaFiltro: undefined as string | null | undefined, puedoEditar: false, etiquetaUnidad: 'Unidad',
  });

  ngOnChanges(): void {
    this.contexto.set({
      temas: this.temas, temaFiltro: this.temaFiltro, puedoEditar: this.puedoEditar, etiquetaUnidad: this.etiquetaUnidad,
    });
    this.lista.reiniciar();
  }

  readonly materiales = signal<Material[]>([]);

  /** Los materiales de la unidad elegida arriba ({@link temaFiltro}), con el título de su unidad. */
  private readonly filas = computed<FilaMaterial[]>(() => {
    const { temaFiltro } = this.contexto();
    return this.materiales()
      .filter(material => temaFiltro === undefined || material.topicId === temaFiltro)
      .map(material => ({ ...material, temaTitulo: this.tituloDelTema(material.topicId) }));
  });

  /** Con una unidad ya elegida arriba, la columna y el filtro de unidad sobran. */
  private readonly mostrarUnidad = () => this.contexto().temaFiltro === undefined && this.contexto().temas.length > 0;

  readonly lista = listaLocal(() => this.filas(), {
    placeholderBusqueda: 'Buscar material',
    textos: material => [material.title],
    selectores: {
      unidad: {
        placeholder: 'Todas',
        visible: this.mostrarUnidad,
        opciones: () => [
          ...this.contexto().temas.map(tema => ({ valor: tema.id, etiqueta: tema.title })),
          { valor: SIN_UNIDAD, etiqueta: this.etiquetaSinUnidad },
        ],
        encaja: (material, valor) => valor === SIN_UNIDAD ? !material.topicId : material.topicId === valor,
      },
      tipo: {
        placeholder: 'Todos los tipos',
        opciones: () => [
          { valor: 'pdf', etiqueta: 'PDF' },
          { valor: 'doc', etiqueta: 'Documento' },
          { valor: 'link', etiqueta: 'Enlace' },
          { valor: 'video', etiqueta: 'Vídeo' },
          { valor: 'other', etiqueta: 'Otro' },
        ],
        encaja: (material, valor) => material.type === valor,
      },
    },
    comparadores: {
      title: (a, b) => comparar(a.title, b.title),
      type: (a, b) => comparar(a.type, b.type),
      temaTitulo: (a, b) => comparar(a.temaTitulo, b.temaTitulo),
      availableFrom: (a, b) => comparar(a.availableFrom, b.availableFrom),
    },
  });

  readonly columnas = computed<ColumnaTabla[]>(() => [
    { campo: 'title', titulo: 'Material', ordenable: true },
    { campo: 'type', titulo: 'Tipo', ordenable: true },
    ...(this.mostrarUnidad() ? [{ campo: 'temaTitulo', titulo: this.contexto().etiquetaUnidad, ordenable: true }] : []),
    { campo: 'availableFrom', titulo: 'Visible desde', ordenable: true },
    ...(this.contexto().puedoEditar ? [{ campo: 'acciones', titulo: '', ancho: '1px', bloqueaClicFila: true }] : []),
  ]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly borrando = signal<string | null>(null);

  readonly dialogoAbierto = signal(false);
  readonly guardando = signal(false);
  readonly errorFormulario = signal<string | null>(null);
  readonly editando = signal<Material | null>(null);

  readonly formulario = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(200)]],
    fileUrl: [''],
    type: this.fb.nonNullable.control<TipoMaterial | ''>(''),
    availableFrom: [''],
    topicId: [''],
  });

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);

    this.clasesService.materiales(this.claseId).subscribe({
      next: (materiales) => {
        this.materiales.set(materiales);
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(AvisoComponent.mensajeDe(err, 'No se han podido cargar los materiales'));
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

  abrirCrear(): void {
    this.editando.set(null);
    this.formulario.reset({ title: '', fileUrl: '', type: '', availableFrom: '', topicId: this.temaFiltro ?? '' });
    this.errorFormulario.set(null);
    this.dialogoAbierto.set(true);
  }

  abrirEditar(material: Material): void {
    this.editando.set(material);
    this.formulario.reset({
      title: material.title,
      fileUrl: material.fileUrl ?? '',
      type: material.type ?? '',
      availableFrom: material.availableFrom ? this.aFechaLocal(material.availableFrom) : '',
      topicId: material.topicId ?? '',
    });
    this.errorFormulario.set(null);
    this.dialogoAbierto.set(true);
  }

  /** El input datetime-local pinta en hora local; la API guarda un instante en UTC. */
  private aFechaLocal(iso: string): string {
    const fecha = new Date(iso);
    const offset = fecha.getTimezoneOffset() * 60000;
    return new Date(fecha.getTime() - offset).toISOString().slice(0, 16);
  }

  guardar(): void {
    this.formulario.markAllAsTouched();

    if (this.formulario.invalid || this.guardando()) {
      return;
    }

    this.guardando.set(true);
    this.errorFormulario.set(null);

    const { title, fileUrl, type, availableFrom, topicId } = this.formulario.getRawValue();
    // El @Pattern del backend admite null pero no "": sin esto, dejar el tipo
    // sin especificar devolvía 400 en vez de guardar el material.
    const datos = {
      title,
      fileUrl: fileUrl || undefined,
      type: type || undefined,
      availableFrom: availableFrom ? new Date(availableFrom).toISOString() : null,
      topicId: topicId || null,
    };
    const edicion = this.editando();

    const peticion = edicion
      ? this.materialesService.actualizar(edicion.id, datos)
      : this.clasesService.crearMaterial(this.claseId, datos);

    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.dialogoAbierto.set(false);
        this.cargar();
      },
      error: (err) => {
        this.guardando.set(false);
        this.errorFormulario.set(AvisoComponent.mensajeDe(err));
      },
    });
  }

  async borrar(material: Material): Promise<void> {
    const confirmado = await this.confirmacion.preguntar(`¿Retirar "${material.title}" de los materiales?`, {
      titulo: 'Retirar material', textoConfirmar: 'Retirar',
    });
    if (!confirmado) {
      return;
    }

    this.borrando.set(material.id);
    this.error.set(null);

    this.materialesService.eliminar(material.id).subscribe({
      next: () => {
        this.borrando.set(null);
        this.materiales.update(lista => lista.filter(m => m.id !== material.id));
      },
      error: (err) => {
        this.borrando.set(null);
        this.error.set(AvisoComponent.mensajeDe(err));
      },
    });
  }
}
