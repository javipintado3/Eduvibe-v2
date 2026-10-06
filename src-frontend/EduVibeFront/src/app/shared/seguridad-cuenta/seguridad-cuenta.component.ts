import { Component, inject, signal } from '@angular/core';
import { NgIf } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AuthService } from '../../core/services/auth.service';
import { AvisoComponent } from '../aviso/aviso.component';

/**
 * Seguridad de la cuenta, dentro del perfil.
 *
 * Se pide la contraseña actual aunque la sesión ya esté abierta: así quien se
 * encuentre el ordenador desbloqueado no puede quedarse con la cuenta.
 */
@Component({
  selector: 'app-seguridad-cuenta',
  standalone: true,
  imports: [NgIf, ReactiveFormsModule, AvisoComponent],
  templateUrl: './seguridad-cuenta.component.html',
})
export class SeguridadCuentaComponent {

  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);

  readonly formulario = this.fb.nonNullable.group({
    actual: ['', [Validators.required]],
    nueva: ['', [Validators.required, Validators.minLength(8)]],
    repetir: ['', [Validators.required]],
  });

  readonly abierto = signal(false);
  readonly enviando = signal(false);
  readonly cambiada = signal(false);
  readonly error = signal<string | null>(null);

  get noCoinciden(): boolean {
    const { nueva, repetir } = this.formulario.getRawValue();
    return !!repetir && this.formulario.controls.repetir.touched && nueva !== repetir;
  }

  campoInvalido(nombre: 'actual' | 'nueva' | 'repetir'): boolean {
    const campo = this.formulario.controls[nombre];
    return campo.invalid && campo.touched;
  }

  alternar(): void {
    this.abierto.update(abierto => !abierto);
    this.cambiada.set(false);
    this.error.set(null);
  }

  guardar(): void {
    this.formulario.markAllAsTouched();

    if (this.formulario.invalid || this.noCoinciden || this.enviando()) {
      return;
    }

    this.enviando.set(true);
    this.error.set(null);

    const { actual, nueva } = this.formulario.getRawValue();

    this.auth.cambiarContrasena(actual, nueva).subscribe({
      next: () => {
        this.enviando.set(false);
        this.cambiada.set(true);
        this.abierto.set(false);
        this.formulario.reset();
      },
      error: (err) => {
        this.enviando.set(false);
        this.error.set(AvisoComponent.mensajeDe(err, 'No se ha podido cambiar la contraseña'));
      },
    });
  }
}
