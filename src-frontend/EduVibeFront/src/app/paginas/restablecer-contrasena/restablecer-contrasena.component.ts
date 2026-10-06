import { Component, Input, inject, signal } from '@angular/core';
import { NgIf } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/services/auth.service';
import { AvisoComponent } from '../../shared/aviso/aviso.component';
import { LogoComponent } from '../../shared/logo/logo.component';

/**
 * Elegir una contraseña nueva con el enlace recibido por correo.
 *
 * A diferencia de la invitación, aquí no se comprueba el enlace antes de
 * enseñar el formulario: no hay a quién mostrar ("esta cuenta es de...") y
 * adelantar esa consulta solo serviría para confirmar si un token existe.
 * Si el enlace no vale, se dice al enviar.
 *
 * Al terminar se manda al login: el cambio no inicia sesión por sí mismo, para
 * que quien solo tenga acceso al correo no entre sin más.
 */
@Component({
  selector: 'app-restablecer-contrasena',
  standalone: true,
  imports: [NgIf, ReactiveFormsModule, RouterLink, AvisoComponent, LogoComponent],
  templateUrl: './restablecer-contrasena.component.html',
  styleUrl: './restablecer-contrasena.component.css',
})
export class RestablecerContrasenaComponent {

  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Llega de la ruta gracias a withComponentInputBinding(). */
  @Input() token = '';

  readonly formulario = this.fb.nonNullable.group({
    password: ['', [Validators.required, Validators.minLength(8)]],
    repetir: ['', [Validators.required]],
  });

  readonly enviando = signal(false);
  readonly error = signal<string | null>(null);

  get noCoinciden(): boolean {
    const { password, repetir } = this.formulario.getRawValue();
    return !!repetir && this.formulario.controls.repetir.touched && password !== repetir;
  }

  campoInvalido(nombre: 'password' | 'repetir'): boolean {
    const campo = this.formulario.controls[nombre];
    return campo.invalid && campo.touched;
  }

  guardar(): void {
    this.formulario.markAllAsTouched();

    if (this.formulario.invalid || this.noCoinciden || this.enviando()) {
      return;
    }

    this.enviando.set(true);
    this.error.set(null);

    this.auth.restablecerContrasena(this.token, this.formulario.getRawValue().password).subscribe({
      next: () => this.router.navigateByUrl('/login'),
      error: (err) => {
        this.enviando.set(false);
        this.error.set(AvisoComponent.mensajeDe(err));
      },
    });
  }
}
