import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin } from 'rxjs';

import { ConfirmacionService } from '../../../core/services/confirmacion.service';
import { UsuariosService } from '../../../core/services/usuarios.service';
import { EstadoCuenta, Invitacion, Pagina, ResultadoImportacion, Rol, Usuario } from '../../../core/models';
import { AvatarComponent } from '../../../shared/avatar/avatar.component';
import { AvisoComponent } from '../../../shared/aviso/aviso.component';
import { DialogoComponent } from '../../../shared/dialogo/dialogo.component';
import { PastillaEstadoComponent } from '../../../shared/pastilla-estado/pastilla-estado.component';
import { FechaPipe } from '../../../shared/pipes/fecha.pipe';
import { CeldaTablaDirective } from '../../../shared/tabla-datos/celda-tabla.directive';
import { TablaDatosComponent } from '../../../shared/tabla-datos/tabla-datos.component';
import { ColumnaTabla, FiltroTabla, OrdenTabla } from '../../../shared/tabla-datos/tabla-datos.tipos';

/**
 * Panel de usuarios.
 *
 * Las altas no llevan contraseña: se crea la cuenta y el sistema emite una
 * invitación de un solo uso. El enlace se muestra aquí además de enviarse por
 * correo, porque en un despliegue de demostración lo normal es que no haya
 * servidor de correo configurado y sin el enlace a la vista el flujo quedaría
 * cortado.
 *
 * No hay botón de borrar: solo desactivar. Un centro no puede perder el
 * histórico académico de alguien porque se pulse mal.
 */
@Component({
  selector: 'app-usuarios',
  standalone: true,
  imports: [
    NgIf, NgFor, ReactiveFormsModule,
    AvatarComponent, PastillaEstadoComponent, DialogoComponent, AvisoComponent, FechaPipe,
    TablaDatosComponent, CeldaTablaDirective,
  ],
  templateUrl: './usuarios.component.html',
  styleUrl: './usuarios.component.css',
})
export class UsuariosComponent implements OnInit {

  private readonly usuariosService = inject(UsuariosService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);

  readonly pagina = signal<Pagina<Usuario> | null>(null);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly exito = signal<string | null>(null);

  // --- acciones sobre la selección ---
  // Como en Fortress, no hay botones por fila: se marcan filas y la barra de
  // acciones ofrece solo lo que tiene sentido para lo marcado (activar si hay
  // alguien desactivado, desactivar si hay alguien activo...).
  // La selección se vacía al cambiar de página o de filtros, así que siempre
  // son filas que se están viendo.
  readonly seleccionados = signal<Set<string>>(new Set());
  readonly aplicandoLote = signal(false);

  private readonly usuariosSeleccionados = computed(() =>
    (this.pagina()?.contenido ?? []).filter(usuario => this.seleccionados().has(usuario.id)));

  readonly activables = computed(() => this.usuariosSeleccionados().filter(u => u.status === 'disabled'));
  readonly desactivables = computed(() => this.usuariosSeleccionados().filter(u => u.status === 'active'));
  readonly pendientes = computed(() => this.usuariosSeleccionados().filter(u => u.status === 'pending'));

  /** La tabla propone el nuevo conjunto (marcar una fila, o todas las de la página); aquí solo se guarda. */
  seleccionCambia(nuevos: Set<string>): void {
    this.seleccionados.set(nuevos);
  }

  limpiarSeleccion(): void {
    this.seleccionados.set(new Set());
  }

  /** Activa o desactiva de golpe las cuentas indicadas. */
  async cambiarEstadoLote(usuarios: Usuario[], status: 'active' | 'disabled'): Promise<void> {
    if (!usuarios.length || this.aplicandoLote()) {
      return;
    }

    const confirmado = await this.confirmacion.preguntar(
      `¿${status === 'active' ? 'Activar' : 'Desactivar'} ${usuarios.length} cuenta(s)?`,
      { titulo: status === 'active' ? 'Activar cuentas' : 'Desactivar cuentas', peligro: status === 'disabled' });
    if (!confirmado) {
      return;
    }

    this.aplicandoLote.set(true);
    this.error.set(null);
    this.exito.set(null);

    forkJoin(usuarios.map(usuario => this.usuariosService.cambiarEstado(usuario.id, status))).subscribe({
      next: () => {
        this.aplicandoLote.set(false);
        this.cargar(this.pagina()?.pagina ?? 0);
      },
      error: (err) => {
        this.aplicandoLote.set(false);
        // Alguna cuenta del lote puede haber cambiado antes del fallo (por
        // ejemplo, la propia cuenta de quien administra no se puede
        // desactivar); se recarga para reflejar lo que sí se aplicó.
        this.error.set(AvisoComponent.mensajeDe(err));
        this.cargar(this.pagina()?.pagina ?? 0);
      },
    });
  }

  /**
   * Reenvía la invitación a las cuentas pendientes. Con una sola se enseña el
   * enlace en el diálogo (en una demo puede no haber correo); con varias solo
   * se avisa de cuántas se han reenviado.
   */
  reenviarLote(usuarios: Usuario[]): void {
    if (usuarios.length === 1) {
      this.reenviarInvitacion(usuarios[0]);
      return;
    }
    if (!usuarios.length || this.aplicandoLote()) {
      return;
    }

    this.aplicandoLote.set(true);
    this.error.set(null);
    this.exito.set(null);

    forkJoin(usuarios.map(usuario => this.usuariosService.reenviarInvitacion(usuario.id))).subscribe({
      next: () => {
        this.aplicandoLote.set(false);
        this.exito.set(`Invitación reenviada a ${usuarios.length} cuentas`);
        this.limpiarSeleccion();
      },
      error: (err) => {
        this.aplicandoLote.set(false);
        this.error.set(AvisoComponent.mensajeDe(err));
      },
    });
  }

  // --- CSV ---
  readonly exportando = signal(false);
  readonly dialogoImportarAbierto = signal(false);
  readonly ficheroImportar = signal<File | null>(null);
  readonly arrastrandoCsv = signal(false);
  readonly importando = signal(false);
  readonly errorImportar = signal<string | null>(null);
  readonly resultadoImportar = signal<ResultadoImportacion | null>(null);

  exportar(): void {
    if (this.exportando()) {
      return;
    }
    this.exportando.set(true);
    this.error.set(null);

    this.usuariosService.exportarCsv({
      q: this.busqueda(),
      role: this.rol() as Rol | '',
      status: this.estado() as EstadoCuenta | '',
    }).subscribe({
      next: (blob) => {
        this.exportando.set(false);
        this.descargar(blob, 'usuarios.csv');
      },
      error: (err) => {
        this.exportando.set(false);
        this.error.set(AvisoComponent.mensajeDe(err, 'No se ha podido exportar'));
      },
    });
  }

  private descargar(blob: Blob, nombre: string): void {
    const url = URL.createObjectURL(blob);
    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = nombre;
    // Algunos navegadores solo disparan la descarga si el <a> está en el
    // documento en el momento del click, no basta con crearlo al vuelo.
    document.body.appendChild(enlace);
    enlace.click();
    enlace.remove();
    URL.revokeObjectURL(url);
  }

  abrirImportar(): void {
    this.ficheroImportar.set(null);
    this.arrastrandoCsv.set(false);
    this.errorImportar.set(null);
    this.resultadoImportar.set(null);
    this.dialogoImportarAbierto.set(true);
  }

  ficheroElegido(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    this.elegirFichero(input.files?.[0] ?? null);
    input.value = '';
  }

  onDragOverCsv(evento: DragEvent): void {
    evento.preventDefault();
    this.arrastrandoCsv.set(true);
  }

  onDragLeaveCsv(evento: DragEvent): void {
    evento.preventDefault();
    this.arrastrandoCsv.set(false);
  }

  onDropCsv(evento: DragEvent): void {
    evento.preventDefault();
    this.arrastrandoCsv.set(false);
    this.elegirFichero(evento.dataTransfer?.files?.[0] ?? null);
  }

  private elegirFichero(fichero: File | null): void {
    this.ficheroImportar.set(fichero);
    this.errorImportar.set(fichero && !fichero.name.toLowerCase().endsWith('.csv')
      ? 'Ese archivo no parece un CSV' : null);
  }

  quitarFichero(): void {
    this.ficheroImportar.set(null);
    this.errorImportar.set(null);
  }

  /** Tamaño legible del fichero elegido, para el chip del dropzone. */
  tamanoLegible(bytes: number): string {
    return bytes < 1024 ? `${bytes} B` : `${(bytes / 1024).toFixed(1)} KB`;
  }

  importarCsv(): void {
    const fichero = this.ficheroImportar();
    if (!fichero || this.errorImportar() || this.importando()) {
      return;
    }

    this.importando.set(true);
    this.errorImportar.set(null);

    this.usuariosService.importar(fichero).subscribe({
      next: (resultado) => {
        this.importando.set(false);
        this.resultadoImportar.set(resultado);
        this.cargar();
      },
      error: (err) => {
        this.importando.set(false);
        this.errorImportar.set(AvisoComponent.mensajeDe(err, 'No se ha podido importar el fichero'));
      },
    });
  }

  // --- tabla: columnas y filtros ---
  // El estado de los filtros vive aquí, en señales; la tabla solo los pinta
  // y avisa de los cambios (igual que los `useState` + `filters` de Fortress).
  readonly busqueda = signal('');
  readonly rol = signal('');
  readonly estado = signal('');
  readonly orden = signal<OrdenTabla | null>(null);

  readonly columnas: ColumnaTabla[] = [
    { campo: 'name', titulo: 'Persona', ordenable: true },
    { campo: 'role', titulo: 'Rol', ordenable: true },
    { campo: 'status', titulo: 'Estado', ordenable: true },
    { campo: 'createdAt', titulo: 'Alta', ordenable: true },
  ];

  readonly filtros = computed<FiltroTabla[]>(() => [
    {
      clave: 'q', tipo: 'busqueda', valor: this.busqueda(),
      placeholder: 'Buscar por nombre o email',
      alCambiar: valor => { this.busqueda.set(valor); this.cargar(); },
    },
    {
      clave: 'role', tipo: 'seleccion', valor: this.rol(), placeholder: 'Cualquier rol',
      opciones: [
        { valor: 'admin', etiqueta: 'Administración' },
        { valor: 'teacher', etiqueta: 'Profesorado' },
        { valor: 'student', etiqueta: 'Alumnado' },
        { valor: 'guardian', etiqueta: 'Tutor legal' },
      ],
      alCambiar: valor => { this.rol.set(valor); this.cargar(); },
    },
    {
      clave: 'status', tipo: 'seleccion', valor: this.estado(), placeholder: 'Cualquier estado',
      opciones: [
        { valor: 'pending', etiqueta: 'Pendiente' },
        { valor: 'active', etiqueta: 'Activa' },
        { valor: 'disabled', etiqueta: 'Desactivada' },
      ],
      alCambiar: valor => { this.estado.set(valor); this.cargar(); },
    },
  ]);

  abrirUsuario(usuario: Usuario): void {
    this.router.navigate(['/admin/usuarios', usuario.id]);
  }

  cambiarOrden(orden: OrdenTabla | null): void {
    this.orden.set(orden);
    this.cargar();
  }

  // --- alta ---
  readonly dialogoAbierto = signal(false);
  readonly creando = signal(false);
  readonly errorFormulario = signal<string | null>(null);
  readonly invitacionEmitida = signal<{ nombre: string; invitacion: Invitacion } | null>(null);
  readonly enlaceCopiado = signal(false);

  readonly formulario = this.fb.nonNullable.group({
    name: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    role: ['student' as Rol, [Validators.required]],
  });

  ngOnInit(): void {
    this.cargar();
  }

  cargar(pagina = 0): void {
    this.cargando.set(true);
    this.error.set(null);
    this.limpiarSeleccion();

    const orden = this.orden();

    this.usuariosService.listar({
      q: this.busqueda(),
      role: this.rol() as Rol | '',
      status: this.estado() as EstadoCuenta | '',
      sort: orden ? `${orden.campo},${orden.direccion}` : undefined,
      page: pagina,
    }).subscribe({
      next: (resultado) => {
        this.pagina.set(resultado);
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(AvisoComponent.mensajeDe(err, 'No se han podido cargar los usuarios'));
        this.cargando.set(false);
      },
    });
  }

  limpiarFiltros(): void {
    this.busqueda.set('');
    this.rol.set('');
    this.estado.set('');
    this.cargar();
  }

  // ------------------------------------------------------------------ alta

  abrirDialogo(): void {
    this.formulario.reset({ name: '', email: '', role: 'student' });
    this.errorFormulario.set(null);
    this.invitacionEmitida.set(null);
    this.enlaceCopiado.set(false);
    this.dialogoAbierto.set(true);
  }

  crear(): void {
    this.formulario.markAllAsTouched();

    if (this.formulario.invalid || this.creando()) {
      return;
    }

    this.creando.set(true);
    this.errorFormulario.set(null);

    const datos = this.formulario.getRawValue();

    this.usuariosService.crear(datos).subscribe({
      next: (resultado) => {
        this.creando.set(false);
        // El diálogo no se cierra: ahora muestra el enlace de invitación, que
        // es lo único que el administrador necesita llevarse de aquí
        this.invitacionEmitida.set({ nombre: resultado.user.name, invitacion: resultado.invitation });
        this.cargar();
      },
      error: (err) => {
        this.creando.set(false);
        this.errorFormulario.set(AvisoComponent.mensajeDe(err));
      },
    });
  }

  copiarEnlace(enlace: string): void {
    navigator.clipboard?.writeText(enlace).then(
      () => {
        this.enlaceCopiado.set(true);
        setTimeout(() => this.enlaceCopiado.set(false), 2200);
      },
      () => { /* sin portapapeles el enlace sigue visible para copiarlo a mano */ }
    );
  }

  // --------------------------------------------------------------- acciones

  private reenviarInvitacion(usuario: Usuario): void {
    if (this.aplicandoLote()) {
      return;
    }
    this.aplicandoLote.set(true);
    this.error.set(null);
    this.exito.set(null);

    this.usuariosService.reenviarInvitacion(usuario.id).subscribe({
      next: (invitacion) => {
        this.aplicandoLote.set(false);
        this.invitacionEmitida.set({ nombre: usuario.name, invitacion });
        this.dialogoAbierto.set(true);
      },
      error: (err) => {
        this.aplicandoLote.set(false);
        this.error.set(AvisoComponent.mensajeDe(err));
      },
    });
  }
}
