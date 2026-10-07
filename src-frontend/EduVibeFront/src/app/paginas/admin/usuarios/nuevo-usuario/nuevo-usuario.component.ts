import { Component, inject, signal } from '@angular/core';
import { NgIf } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { UsuariosService } from '../../../../core/services/usuarios.service';
import { Invitacion, Rol } from '../../../../core/models';
import { AvisoComponent } from '../../../../shared/aviso/aviso.component';
import { CampoFormularioComponent } from '../../../../shared/formulario/campo-formulario.component';
import { PaginaFormularioComponent } from '../../../../shared/formulario/pagina-formulario.component';
import { SeccionFormularioComponent } from '../../../../shared/formulario/seccion-formulario.component';
import { FechaPipe } from '../../../../shared/pipes/fecha.pipe';

type Campo = 'name' | 'email';

/**
 * Alta de un usuario. Es una página y no una ventana.
 *
 * Las altas no llevan contraseña: se crea la cuenta y el sistema emite una
 * invitación de un solo uso. Al guardar no se navega: la misma página pasa a
 * enseñar el enlace, porque es lo único que quien administra necesita llevarse
 * de aquí (en una demo puede no haber servidor de correo y sin el enlace a la
 * vista el flujo quedaría cortado).
 */
@Component({
  selector: 'app-nuevo-usuario',
  standalone: true,
  imports: [
    NgIf, RouterLink, ReactiveFormsModule,
    PaginaFormularioComponent, SeccionFormularioComponent, CampoFormularioComponent,
    AvisoComponent, FechaPipe,
  ],
  templateUrl: './nuevo-usuario.component.html',
  styleUrl: './nuevo-usuario.component.css',
})
export class NuevoUsuarioComponent {

  private readonly usuariosService = inject(UsuariosService);
  private readonly fb = inject(FormBuilder);

  readonly guardando = signal(false);
  readonly error = signal<string | null>(null);

  /** Paso 2: lo que se muestra una vez creada la cuenta. */
  readonly invitacionEmitida = signal<{ nombre: string; invitacion: Invitacion } | null>(null);
  readonly enlaceCopiado = signal(false);

  readonly formulario = this.fb.nonNullable.group({
    name: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    role: ['student' as Rol, [Validators.required]],
  });

  private readonly mensajes: Record<Campo, { [error: string]: string }> = {
    name: { required: 'Indica el nombre y apellidos' },
    email: { required: 'Indica el email', email: 'El email no es válido' },
  };

  /** Error de un campo, solo cuando ya se ha tocado o se ha intentado guardar. */
  errorDe(campo: Campo): string | null {
    const control = this.formulario.controls[campo];
    if (!control.touched || !control.errors) {
      return null;
    }
    const clave = Object.keys(control.errors)[0];
    return this.mensajes[campo][clave] ?? 'Valor no válido';
  }

  recortar(campo: Campo): void {
    const control = this.formulario.controls[campo];
    control.setValue(control.value.trim());
  }

  guardar(): void {
    this.formulario.markAllAsTouched();

    if (this.formulario.invalid || this.guardando()) {
      return;
    }

    this.guardando.set(true);
    this.error.set(null);

    const datos = this.formulario.getRawValue();

    this.usuariosService.crear(datos).subscribe({
      next: (resultado) => {
        this.guardando.set(false);
        this.invitacionEmitida.set({ nombre: resultado.user.name, invitacion: resultado.invitation });
      },
      error: (err) => {
        this.guardando.set(false);
        this.error.set(AvisoComponent.mensajeDe(err));
      },
    });
  }

  /** Vuelve al formulario en blanco para dar de alta a otra persona. */
  crearOtro(): void {
    this.formulario.reset({ name: '', email: '', role: 'student' });
    this.error.set(null);
    this.enlaceCopiado.set(false);
    this.invitacionEmitida.set(null);
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
