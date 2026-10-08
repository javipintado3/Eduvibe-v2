import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { RouterLink } from '@angular/router';

import { ConfirmacionService } from '../../../core/services/confirmacion.service';
import { SolicitudesRegistroService } from '../../../core/services/solicitudes-registro.service';
import { EstadoSolicitud, Invitacion, Pagina, Rol, SolicitudRegistro } from '../../../core/models';
import { AvatarComponent } from '../../../shared/avatar/avatar.component';
import { AvisoComponent } from '../../../shared/aviso/aviso.component';
import { DialogoComponent } from '../../../shared/dialogo/dialogo.component';
import { PastillaEstadoComponent } from '../../../shared/pastilla-estado/pastilla-estado.component';
import { FechaPipe } from '../../../shared/pipes/fecha.pipe';
import { CeldaTablaDirective } from '../../../shared/tabla-datos/celda-tabla.directive';
import { TablaDatosComponent } from '../../../shared/tabla-datos/tabla-datos.component';
import { ColumnaTabla, FiltroTabla } from '../../../shared/tabla-datos/tabla-datos.tipos';

/**
 * Solicitudes de registro.
 *
 * Quien pide una cuenta no elige su rol: lo elige aquí quien la acepta, con
 * "Alumnado" como valor por defecto por ser el menos privilegiado. Aceptar crea
 * la cuenta y su invitación; el enlace se enseña además de enviarse por correo,
 * porque en una demo lo normal es que no haya servidor de correo.
 */
@Component({
  selector: 'app-solicitudes',
  standalone: true,
  imports: [
    NgIf, NgFor, RouterLink,
    AvatarComponent, AvisoComponent, DialogoComponent, PastillaEstadoComponent, FechaPipe,
    TablaDatosComponent, CeldaTablaDirective,
  ],
  templateUrl: './solicitudes.component.html',
  styleUrl: './solicitudes.component.css',
})
export class SolicitudesComponent implements OnInit {

  private readonly solicitudesService = inject(SolicitudesRegistroService);
  private readonly confirmacion = inject(ConfirmacionService);

  readonly pagina = signal<Pagina<SolicitudRegistro> | null>(null);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly exito = signal<string | null>(null);

  /** Id de la solicitud que se está resolviendo, para bloquear sus botones mientras tanto. */
  readonly resolviendo = signal<string | null>(null);

  readonly estado = signal<EstadoSolicitud>('pending');

  /** Rol elegido para cada solicitud; la que no aparece aquí es alumnado. */
  private readonly roles = signal<Record<string, Rol>>({});

  readonly columnas: ColumnaTabla[] = [
    { campo: 'name', titulo: 'Persona' },
    { campo: 'message', titulo: 'Mensaje' },
    { campo: 'status', titulo: 'Estado' },
    { campo: 'createdAt', titulo: 'Solicitada' },
    { campo: 'acciones', titulo: '', ancho: '1px', bloqueaClicFila: true },
  ];

  readonly filtros = computed<FiltroTabla[]>(() => [
    {
      clave: 'status', tipo: 'seleccion', valor: this.estado(), placeholder: 'Pendientes de decisión',
      activo: this.estado() !== 'pending',
      opciones: [
        { valor: 'pending', etiqueta: 'Pendientes de decisión' },
        { valor: 'unverified', etiqueta: 'Sin confirmar el correo' },
        { valor: 'approved', etiqueta: 'Aceptadas' },
        { valor: 'rejected', etiqueta: 'Rechazadas' },
      ],
      alCambiar: valor => { this.estado.set((valor || 'pending') as EstadoSolicitud); this.cargar(); },
    },
  ]);

  // --- invitación emitida al aceptar ---
  readonly dialogoAbierto = signal(false);
  readonly invitacionEmitida = signal<{ nombre: string; invitacion: Invitacion } | null>(null);
  readonly enlaceCopiado = signal(false);

  ngOnInit(): void {
    this.cargar();
  }

  cargar(pagina = 0): void {
    this.cargando.set(true);
    this.error.set(null);

    this.solicitudesService.listar({ status: this.estado(), page: pagina }).subscribe({
      next: (resultado) => {
        this.pagina.set(resultado);
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(AvisoComponent.mensajeDe(err, 'No se han podido cargar las solicitudes'));
        this.cargando.set(false);
      },
    });
  }

  limpiarFiltros(): void {
    this.estado.set('pending');
    this.cargar();
  }

  rolDe(solicitud: SolicitudRegistro): Rol {
    return this.roles()[solicitud.id] ?? 'student';
  }

  elegirRol(solicitud: SolicitudRegistro, rol: string): void {
    this.roles.update(actuales => ({ ...actuales, [solicitud.id]: rol as Rol }));
  }

  aprobar(solicitud: SolicitudRegistro): void {
    if (this.resolviendo()) {
      return;
    }
    this.resolviendo.set(solicitud.id);
    this.error.set(null);
    this.exito.set(null);

    this.solicitudesService.aprobar(solicitud.id, this.rolDe(solicitud)).subscribe({
      next: (creado) => {
        this.resolviendo.set(null);
        this.invitacionEmitida.set({ nombre: solicitud.name, invitacion: creado.invitation });
        this.dialogoAbierto.set(true);
        this.cargar(this.pagina()?.pagina ?? 0);
      },
      error: (err) => {
        this.resolviendo.set(null);
        this.error.set(AvisoComponent.mensajeDe(err));
      },
    });
  }

  async rechazar(solicitud: SolicitudRegistro): Promise<void> {
    if (this.resolviendo()) {
      return;
    }

    const confirmado = await this.confirmacion.preguntar(
      `¿Rechazar la solicitud de ${solicitud.name}? Se le avisará por correo y no podrá volver a pedirla en 30 días.`,
      { titulo: 'Rechazar solicitud', peligro: true });
    if (!confirmado) {
      return;
    }

    this.resolviendo.set(solicitud.id);
    this.error.set(null);
    this.exito.set(null);

    this.solicitudesService.rechazar(solicitud.id).subscribe({
      next: () => {
        this.resolviendo.set(null);
        this.exito.set(`Solicitud de ${solicitud.name} rechazada`);
        this.cargar(this.pagina()?.pagina ?? 0);
      },
      error: (err) => {
        this.resolviendo.set(null);
        this.error.set(AvisoComponent.mensajeDe(err));
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
}
