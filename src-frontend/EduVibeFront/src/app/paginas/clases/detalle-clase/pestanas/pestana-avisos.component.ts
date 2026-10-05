import { Component, Input, OnChanges, OnInit, computed, inject, signal } from '@angular/core';
import { NgIf } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ClasesService } from '../../../../core/services/clases.service';
import { ConfirmacionService } from '../../../../core/services/confirmacion.service';
import { Anuncio } from '../../../../core/models';
import { AvisoComponent } from '../../../../shared/aviso/aviso.component';
import { CargandoComponent } from '../../../../shared/cargando/cargando.component';
import { DialogoComponent } from '../../../../shared/dialogo/dialogo.component';
import { FechaPipe } from '../../../../shared/pipes/fecha.pipe';
import { CeldaTablaDirective } from '../../../../shared/tabla-datos/celda-tabla.directive';
import { TablaDatosComponent } from '../../../../shared/tabla-datos/tabla-datos.component';
import { ColumnaTabla } from '../../../../shared/tabla-datos/tabla-datos.tipos';
import { comparar, listaLocal } from '../../../../core/utils/lista-local';
import { todasLasPaginas } from '../../../../core/utils/paginacion';

/**
 * Pestaña "Avisos": el muro de la clase.
 *
 * Solo el profesorado de la clase (o administración) publica y retira avisos;
 * `puedoEditar` llega del padre, que ya lo sabe por el detalle de la clase.
 * Quién puede borrar CADA aviso concreto lo decide la API en `puedoBorrar`.
 */
@Component({
  selector: 'app-pestana-avisos',
  standalone: true,
  imports: [NgIf, ReactiveFormsModule, CargandoComponent, DialogoComponent, AvisoComponent, FechaPipe,
    TablaDatosComponent, CeldaTablaDirective],
  templateUrl: './pestana-avisos.component.html',
  styleUrl: './pestana-avisos.component.css',
})
export class PestanaAvisosComponent implements OnInit, OnChanges {

  private readonly clasesService = inject(ClasesService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly fb = inject(FormBuilder);

  @Input({ required: true }) claseId!: string;
  @Input() puedoEditar = false;

  /**
   * Todos los avisos de la clase: se piden entero (son pocos y de una sola clase) para poder
   * buscar, filtrar y ordenar en el cliente. Sin orden elegido se respeta el del servidor,
   * que deja los fijados arriba.
   */
  readonly avisos = signal<Anuncio[]>([]);

  readonly lista = listaLocal(() => this.avisos(), {
    placeholderBusqueda: 'Buscar en los avisos',
    textos: aviso => [aviso.content, aviso.authorName],
    selectores: {
      fijado: {
        placeholder: 'Todos',
        opciones: () => [{ valor: 'si', etiqueta: 'Solo fijados' }],
        encaja: (aviso, valor) => aviso.pinned === (valor === 'si'),
      },
    },
    comparadores: {
      content: (a, b) => comparar(a.content, b.content),
      authorName: (a, b) => comparar(a.authorName, b.authorName),
      createdAt: (a, b) => comparar(a.createdAt, b.createdAt),
    },
  });

  /** `puedoEditar` como señal: la columna de acciones solo existe para quien edita. */
  private readonly editor = signal(false);

  ngOnChanges(): void {
    this.editor.set(this.puedoEditar);
  }

  readonly columnas = computed<ColumnaTabla[]>(() => [
    { campo: 'content', titulo: 'Aviso', ordenable: true },
    { campo: 'authorName', titulo: 'Autor', ordenable: true },
    { campo: 'createdAt', titulo: 'Publicado', ordenable: true },
    ...(this.editor() ? [{ campo: 'acciones', titulo: '', ancho: '1px', bloqueaClicFila: true }] : []),
  ]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly borrando = signal<string | null>(null);

  readonly dialogoAbierto = signal(false);
  readonly publicando = signal(false);
  readonly errorFormulario = signal<string | null>(null);

  readonly formulario = this.fb.nonNullable.group({
    content: ['', [Validators.required, Validators.maxLength(5000)]],
    pinned: [false],
  });

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);

    todasLasPaginas(pagina => this.clasesService.avisos(this.claseId, pagina)).subscribe({
      next: (avisos) => {
        this.avisos.set(avisos);
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(AvisoComponent.mensajeDe(err, 'No se han podido cargar los avisos'));
        this.cargando.set(false);
      },
    });
  }

  abrirDialogo(): void {
    this.formulario.reset({ content: '', pinned: false });
    this.errorFormulario.set(null);
    this.dialogoAbierto.set(true);
  }

  publicar(): void {
    this.formulario.markAllAsTouched();

    if (this.formulario.invalid || this.publicando()) {
      return;
    }

    this.publicando.set(true);
    this.errorFormulario.set(null);

    const { content, pinned } = this.formulario.getRawValue();

    this.clasesService.crearAviso(this.claseId, { content, pinned }).subscribe({
      next: () => {
        this.publicando.set(false);
        this.dialogoAbierto.set(false);
        // El aviso nuevo va arriba del muro, así que se vuelve a la primera página
        this.lista.irA(0);
        this.cargar();
      },
      error: (err) => {
        this.publicando.set(false);
        this.errorFormulario.set(AvisoComponent.mensajeDe(err));
      },
    });
  }

  async borrar(aviso: Anuncio): Promise<void> {
    const confirmado = await this.confirmacion.preguntar('¿Retirar este aviso del muro?', {
      titulo: 'Retirar aviso', textoConfirmar: 'Retirar',
    });
    if (!confirmado) {
      return;
    }

    this.borrando.set(aviso.id);
    this.error.set(null);

    this.clasesService.borrarAviso(aviso.id).subscribe({
      next: () => {
        this.borrando.set(null);
        this.avisos.update(lista => lista.filter(a => a.id !== aviso.id));
      },
      error: (err) => {
        this.borrando.set(null);
        this.error.set(AvisoComponent.mensajeDe(err));
      },
    });
  }
}
