import { Component, inject, signal } from '@angular/core';
import { NgIf } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { AuthService } from '../../core/services/auth.service';
import { PerfilService } from '../../core/services/perfil.service';
import { AvisoComponent } from '../aviso/aviso.component';

/** Palabra que hay que escribir para confirmar: evita borrar la cuenta de un clic por error. */
const PALABRA_DE_CONFIRMACION = 'BORRAR';

/**
 * Privacidad y datos (RGPD): descargar todo lo que la plataforma guarda de la
 * persona, y borrar la cuenta.
 *
 * Borrar no es total y la pantalla lo dice antes de pedir nada: se elimina lo
 * que no forma parte del expediente académico, y se conservan las notas y
 * entregas evaluadas porque el centro tiene que poder certificarlas.
 */
@Component({
  selector: 'app-privacidad-cuenta',
  standalone: true,
  imports: [NgIf, ReactiveFormsModule, AvisoComponent],
  templateUrl: './privacidad-cuenta.component.html',
  styles: [`
    .explicacion {
      background: var(--rojo-50);
      border: 1px solid #fecaca;
      border-radius: var(--radio-sm);
      padding: 13px 15px;
    }
  `],
})
export class PrivacidadCuentaComponent {

  private readonly fb = inject(FormBuilder);
  private readonly perfilService = inject(PerfilService);
  readonly auth = inject(AuthService);

  readonly palabra = PALABRA_DE_CONFIRMACION;

  readonly descargando = signal(false);
  readonly borrando = signal(false);
  readonly confirmando = signal(false);
  readonly error = signal<string | null>(null);

  readonly formulario = this.fb.nonNullable.group({
    password: ['', [Validators.required]],
    codigo: [''],
    confirmacion: ['', [Validators.required]],
  });

  get confirmacionIncorrecta(): boolean {
    const { confirmacion } = this.formulario.getRawValue();
    return confirmacion.trim().toUpperCase() !== PALABRA_DE_CONFIRMACION;
  }

  descargar(): void {
    if (this.descargando()) {
      return;
    }

    this.descargando.set(true);
    this.error.set(null);

    this.perfilService.exportarDatos().subscribe({
      next: (archivo) => {
        // Se crea un enlace temporal al archivo y se pulsa solo, para que el navegador lo guarde
        const url = URL.createObjectURL(archivo);
        const enlace = document.createElement('a');
        enlace.href = url;
        enlace.download = 'eduvibe-mis-datos.json';
        enlace.click();
        URL.revokeObjectURL(url);
        this.descargando.set(false);
      },
      error: (err) => {
        this.descargando.set(false);
        this.error.set(AvisoComponent.mensajeDe(err, 'No se han podido descargar tus datos'));
      },
    });
  }

  alternarBorrado(): void {
    this.confirmando.update(valor => !valor);
    this.formulario.reset();
    this.error.set(null);
  }

  borrar(): void {
    this.formulario.markAllAsTouched();
    if (this.formulario.invalid || this.confirmacionIncorrecta || this.borrando()) {
      return;
    }

    this.borrando.set(true);
    this.error.set(null);

    const { password, codigo } = this.formulario.getRawValue();

    this.perfilService.borrarCuenta(password, codigo.trim() || undefined).subscribe({
      // La cuenta ya está desactivada: se cierra la sesión local sin avisar al servidor
      next: () => this.auth.logout(),
      error: (err) => {
        this.borrando.set(false);
        this.error.set(AvisoComponent.mensajeDe(err, 'No se ha podido borrar la cuenta'));
      },
    });
  }
}
