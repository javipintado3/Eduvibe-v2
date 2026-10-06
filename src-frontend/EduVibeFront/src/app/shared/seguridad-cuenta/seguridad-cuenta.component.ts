import { Component, inject, signal } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import * as QRCode from 'qrcode';

import { AuthService } from '../../core/services/auth.service';
import { ConfiguracionDosPasos } from '../../core/models';
import { AvisoComponent } from '../aviso/aviso.component';

/**
 * Seguridad de la cuenta, dentro del perfil: contraseña y verificación en dos pasos.
 *
 * Se pide la contraseña actual aunque la sesión ya esté abierta: así quien se
 * encuentre el ordenador desbloqueado no puede quedarse con la cuenta ni
 * quitarle la protección.
 */
@Component({
  selector: 'app-seguridad-cuenta',
  standalone: true,
  imports: [NgIf, NgFor, ReactiveFormsModule, AvisoComponent],
  templateUrl: './seguridad-cuenta.component.html',
  styles: [`
    .ficha {
      background: var(--verde-50);
      border: 1px solid var(--verde-100);
      border-radius: var(--radio-sm);
      padding: 13px 15px;
    }
    .lista-codigos {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
      gap: 6px 12px;
    }
  `],
})
export class SeguridadCuentaComponent {

  private readonly fb = inject(FormBuilder);
  readonly auth = inject(AuthService);

  // ------------------------------------------------------------ contraseña

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

  // ------------------------------------------------------ dos pasos (2FA)

  /** Secreto y enlace mientras la persona está configurando la app. */
  readonly configuracion = signal<ConfiguracionDosPasos | null>(null);
  readonly qr = signal<string | null>(null);
  /** Códigos de recuperación recién generados: se enseñan una sola vez. */
  readonly codigosRecuperacion = signal<string[] | null>(null);
  readonly desactivando = signal(false);
  readonly trabajandoDosPasos = signal(false);
  readonly errorDosPasos = signal<string | null>(null);

  readonly formularioActivar = this.fb.nonNullable.group({
    codigo: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  readonly formularioDesactivar = this.fb.nonNullable.group({
    password: ['', [Validators.required]],
    codigo: ['', [Validators.required]],
  });

  empezarDosPasos(): void {
    this.trabajandoDosPasos.set(true);
    this.errorDosPasos.set(null);

    this.auth.iniciarDosPasos().subscribe({
      next: async (configuracion) => {
        // El QR se dibuja en el navegador: el secreto no pasa por ningún servicio externo
        this.qr.set(await QRCode.toDataURL(configuracion.otpauthUri, { width: 180, margin: 1 }));
        this.configuracion.set(configuracion);
        this.trabajandoDosPasos.set(false);
      },
      error: (err) => this.fallarDosPasos(err, 'No se ha podido iniciar la configuración'),
    });
  }

  cancelarConfiguracion(): void {
    this.configuracion.set(null);
    this.qr.set(null);
    this.formularioActivar.reset();
    this.errorDosPasos.set(null);
  }

  confirmarActivacion(): void {
    this.formularioActivar.markAllAsTouched();
    if (this.formularioActivar.invalid || this.trabajandoDosPasos()) {
      return;
    }

    this.trabajandoDosPasos.set(true);
    this.errorDosPasos.set(null);

    this.auth.activarDosPasos(this.formularioActivar.getRawValue().codigo).subscribe({
      next: (respuesta) => {
        this.trabajandoDosPasos.set(false);
        this.codigosRecuperacion.set(respuesta.recoveryCodes);
        this.cancelarConfiguracion();
      },
      error: (err) => this.fallarDosPasos(err, 'No se ha podido activar la verificación'),
    });
  }

  copiarCodigos(): void {
    const codigos = this.codigosRecuperacion();
    if (codigos) {
      navigator.clipboard?.writeText(codigos.join('\n'));
    }
  }

  cerrarCodigos(): void {
    this.codigosRecuperacion.set(null);
  }

  alternarDesactivar(): void {
    this.desactivando.update(valor => !valor);
    this.formularioDesactivar.reset();
    this.errorDosPasos.set(null);
  }

  confirmarDesactivacion(): void {
    this.formularioDesactivar.markAllAsTouched();
    if (this.formularioDesactivar.invalid || this.trabajandoDosPasos()) {
      return;
    }

    this.trabajandoDosPasos.set(true);
    this.errorDosPasos.set(null);

    const { password, codigo } = this.formularioDesactivar.getRawValue();

    this.auth.desactivarDosPasos(password, codigo.trim()).subscribe({
      next: () => {
        this.trabajandoDosPasos.set(false);
        this.desactivando.set(false);
        this.formularioDesactivar.reset();
      },
      error: (err) => this.fallarDosPasos(err, 'No se ha podido desactivar la verificación'),
    });
  }

  private fallarDosPasos(err: unknown, porDefecto: string): void {
    this.trabajandoDosPasos.set(false);
    this.errorDosPasos.set(AvisoComponent.mensajeDe(err, porDefecto));
  }
}
