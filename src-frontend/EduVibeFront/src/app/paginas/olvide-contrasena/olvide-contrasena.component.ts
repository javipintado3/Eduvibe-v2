import { Component, inject, signal } from '@angular/core';
import { NgIf } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/services/auth.service';
import { AvisoComponent } from '../../shared/aviso/aviso.component';
import { LogoComponent } from '../../shared/logo/logo.component';

/**
 * "Olvidé mi contraseña".
 *
 * La respuesta es la misma exista o no el email: decir "ese email no está
 * registrado" permitiría averiguar quién tiene cuenta en la plataforma. Por
 * eso el aviso de éxito habla en condicional ("si existe una cuenta...").
 */
@Component({
  selector: 'app-olvide-contrasena',
  standalone: true,
  imports: [NgIf, ReactiveFormsModule, RouterLink, AvisoComponent, LogoComponent],
  templateUrl: './olvide-contrasena.component.html',
  styleUrl: './olvide-contrasena.component.css',
})
export class OlvideContrasenaComponent {

  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);

  readonly formulario = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  readonly enviando = signal(false);
  readonly enviado = signal(false);
  readonly error = signal<string | null>(null);

  campoInvalido(): boolean {
    const campo = this.formulario.controls.email;
    return campo.invalid && campo.touched;
  }

  enviar(): void {
    this.formulario.markAllAsTouched();

    if (this.formulario.invalid || this.enviando()) {
      return;
    }

    this.enviando.set(true);
    this.error.set(null);

    this.auth.olvideMiContrasena(this.formulario.getRawValue().email).subscribe({
      next: () => {
        this.enviando.set(false);
        this.enviado.set(true);
      },
      error: (err) => {
        this.enviando.set(false);
        this.error.set(AvisoComponent.mensajeDe(err));
      },
    });
  }
}
