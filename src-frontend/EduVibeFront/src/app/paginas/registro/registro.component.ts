import { Component, inject, signal } from '@angular/core';
import { NgIf } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/services/auth.service';
import { AvisoComponent } from '../../shared/aviso/aviso.component';
import { LogoComponent } from '../../shared/logo/logo.component';

/**
 * Solicitud de cuenta.
 *
 * Pedirla no crea ninguna cuenta: la persona confirma su correo y después un
 * administrador decide. Por eso la pantalla no pide contraseña (la elegirá al
 * recibir su invitación) ni deja elegir rol (lo fija quien aprueba).
 *
 * La respuesta es la misma haya o no cuenta con ese email, o corresponda su
 * dominio a un centro: decir "ese correo ya existe" permitiría averiguar quién
 * está dado de alta. Por eso el aviso de éxito habla en condicional.
 *
 * El campo `website` es un cebo contra bots: está fuera de pantalla, así que una
 * persona nunca lo rellena, pero un bot que rellena todo lo que ve sí.
 */
@Component({
  selector: 'app-registro',
  standalone: true,
  imports: [NgIf, ReactiveFormsModule, RouterLink, AvisoComponent, LogoComponent],
  templateUrl: './registro.component.html',
  styleUrl: './registro.component.css',
})
export class RegistroComponent {

  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);

  /** Mismo tope que la API: más largo se rechazaría allí. */
  readonly maxMensaje = 500;

  readonly formulario = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email]],
    message: ['', [Validators.maxLength(this.maxMensaje)]],
    website: [''],
  });

  readonly enviando = signal(false);
  readonly enviado = signal(false);
  readonly error = signal<string | null>(null);

  campoInvalido(nombre: 'name' | 'email' | 'message'): boolean {
    const campo = this.formulario.controls[nombre];
    return campo.invalid && campo.touched;
  }

  enviar(): void {
    this.formulario.markAllAsTouched();

    if (this.formulario.invalid || this.enviando()) {
      return;
    }

    this.enviando.set(true);
    this.error.set(null);

    const { name, email, message, website } = this.formulario.getRawValue();

    this.auth.solicitarRegistro(name.trim(), email.trim(), message.trim(), website).subscribe({
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
