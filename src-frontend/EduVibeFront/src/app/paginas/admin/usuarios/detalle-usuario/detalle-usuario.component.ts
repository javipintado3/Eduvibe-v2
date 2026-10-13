import { Component, Input, OnInit, inject, signal } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ConfirmacionService } from '../../../../core/services/confirmacion.service';
import { UsuariosService } from '../../../../core/services/usuarios.service';
import { ClaseDeUsuario, Rol, Usuario } from '../../../../core/models';
import { AvatarComponent } from '../../../../shared/avatar/avatar.component';
import { AvisoComponent } from '../../../../shared/aviso/aviso.component';
import { CargandoComponent } from '../../../../shared/cargando/cargando.component';
import { EstadoVacioComponent } from '../../../../shared/estado-vacio/estado-vacio.component';
import { PastillaEstadoComponent } from '../../../../shared/pastilla-estado/pastilla-estado.component';
import { FechaPipe } from '../../../../shared/pipes/fecha.pipe';
import { PaginadorComponent } from '../../../../shared/paginador/paginador.component';
import { paginacionLocal } from '../../../../core/utils/paginacion';

/**
 * Ficha de una persona: se llega aquí pinchando su fila en el listado, igual
 * que se entra en una clase pinchando su tarjeta. Editar y gestionar el
 * estado de la cuenta vive aquí, no en un diálogo aparte sobre la lista.
 */
@Component({
  selector: 'app-detalle-usuario',
  standalone: true,
  imports: [
    NgIf, NgFor, RouterLink, ReactiveFormsModule,
    AvatarComponent, CargandoComponent, EstadoVacioComponent, PastillaEstadoComponent, AvisoComponent, FechaPipe,
    PaginadorComponent,
  ],
  templateUrl: './detalle-usuario.component.html',
  styleUrl: './detalle-usuario.component.css',
})
export class DetalleUsuarioComponent implements OnInit {

  /** Diez clases por página en la ficha de la persona. */
  readonly paginacionClases = paginacionLocal(() => this.clases());

  private readonly usuariosService = inject(UsuariosService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  @Input() id = '';

  readonly usuario = signal<Usuario | null>(null);
  readonly clases = signal<ClaseDeUsuario[]>([]);
  readonly cargando = signal(true);
  readonly cargandoClases = signal(true);
  readonly error = signal<string | null>(null);

  readonly guardando = signal(false);
  readonly errorGuardar = signal<string | null>(null);
  readonly guardado = signal(false);

  readonly trabajando = signal(false);
  readonly errorAccion = signal<string | null>(null);

  readonly formulario = this.fb.nonNullable.group({
    name: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    role: ['student' as Rol, [Validators.required]],
  });

  ngOnInit(): void {
    this.cargar();
    this.cargarClases();
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);

    this.usuariosService.obtener(this.id).subscribe({
      next: (usuario) => {
        this.usuario.set(usuario);
        this.formulario.reset({ name: usuario.name, email: usuario.email, role: usuario.role });
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(AvisoComponent.mensajeDe(err, 'No se ha podido cargar la persona'));
        this.cargando.set(false);
      },
    });
  }

  cargarClases(): void {
    this.cargandoClases.set(true);

    this.usuariosService.clasesDe(this.id).subscribe({
      next: (clases) => {
        this.clases.set(clases);
        this.cargandoClases.set(false);
      },
      error: () => {
        // No es crítico: la ficha sigue siendo útil sin la lista de clases.
        this.cargandoClases.set(false);
      },
    });
  }

  guardar(): void {
    this.formulario.markAllAsTouched();

    if (this.formulario.invalid || this.guardando()) {
      return;
    }

    this.guardando.set(true);
    this.errorGuardar.set(null);
    this.guardado.set(false);

    this.usuariosService.actualizar(this.id, this.formulario.getRawValue()).subscribe({
      next: (usuario) => {
        this.guardando.set(false);
        this.usuario.set(usuario);
        this.guardado.set(true);
        setTimeout(() => this.guardado.set(false), 2200);
      },
      error: (err) => {
        this.guardando.set(false);
        this.errorGuardar.set(AvisoComponent.mensajeDe(err));
      },
    });
  }

  cambiarEstado(status: 'active' | 'disabled'): void {
    this.trabajando.set(true);
    this.errorAccion.set(null);

    this.usuariosService.cambiarEstado(this.id, status).subscribe({
      next: (usuario) => {
        this.trabajando.set(false);
        this.usuario.set(usuario);
      },
      error: (err) => {
        this.trabajando.set(false);
        this.errorAccion.set(AvisoComponent.mensajeDe(err));
      },
    });
  }

  async eliminar(usuario: Usuario): Promise<void> {
    if (this.trabajando()) {
      return;
    }

    const confirmado = await this.confirmacion.preguntar(
      `¿Eliminar a ${usuario.name}? Saldrá de todas sus clases y no podrá volver a entrar. `
        + 'Lo que escribió (foros, anuncios, notas) se conserva, pero como «Usuario eliminado». '
        + 'Esta acción no se puede deshacer.',
      { titulo: 'Eliminar usuario', textoConfirmar: 'Eliminar', peligro: true });
    if (!confirmado) {
      return;
    }

    this.trabajando.set(true);
    this.errorAccion.set(null);

    this.usuariosService.eliminar(this.id).subscribe({
      next: () => {
        this.trabajando.set(false);
        this.router.navigate(['/admin/usuarios']);
      },
      error: (err) => {
        this.trabajando.set(false);
        this.errorAccion.set(AvisoComponent.mensajeDe(err));
      },
    });
  }

  reenviarInvitacion(): void {
    this.trabajando.set(true);
    this.errorAccion.set(null);

    this.usuariosService.reenviarInvitacion(this.id).subscribe({
      next: () => {
        this.trabajando.set(false);
        this.cargar();
      },
      error: (err) => {
        this.trabajando.set(false);
        this.errorAccion.set(AvisoComponent.mensajeDe(err));
      },
    });
  }
}
