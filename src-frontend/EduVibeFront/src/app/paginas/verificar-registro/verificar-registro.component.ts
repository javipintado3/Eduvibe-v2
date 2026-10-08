import { Component, Input, inject, signal } from '@angular/core';
import { NgIf } from '@angular/common';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/services/auth.service';
import { AvisoComponent } from '../../shared/aviso/aviso.component';
import { LogoComponent } from '../../shared/logo/logo.component';

/**
 * Confirmación del correo de una solicitud de registro.
 *
 * La confirmación no se hace al abrir la página sino al pulsar el botón. Los
 * antivirus y las vistas previas de algunos clientes de correo abren los
 * enlaces por su cuenta: si abrir el enlace bastara, podrían consumirlo antes
 * de que la persona llegue a verlo.
 */
@Component({
  selector: 'app-verificar-registro',
  standalone: true,
  imports: [NgIf, RouterLink, AvisoComponent, LogoComponent],
  template: `
    <div class="pagina">
      <div class="caja">

        <div class="centrado mb-3">
          <app-logo [size]="36"></app-logo>
        </div>

        <div class="tarjeta">
          <div class="tarjeta-cuerpo">

            <ng-container *ngIf="confirmado(); else pendiente">
              <h2 class="mb-1">Correo confirmado</h2>
              <p class="apagado mb-3">
                Tu solicitud ya está en manos de la administración del centro. Cuando la revise,
                recibirás un correo con el enlace para elegir tu contraseña.
              </p>
              <a routerLink="/login" class="boton boton-secundario boton-bloque">Ir a la pantalla de entrada</a>
            </ng-container>

            <ng-template #pendiente>
              <h2 class="mb-1">Confirma tu correo</h2>
              <p class="apagado mb-3">
                Pulsa el botón para confirmar que este correo es tuyo y enviar tu solicitud de cuenta.
              </p>

              <app-aviso class="mb-2" tipo="error" [mensaje]="error()"></app-aviso>

              <button type="button" class="boton boton-primario boton-bloque"
                      [disabled]="enviando()" (click)="confirmar()">
                {{ enviando() ? 'Confirmando…' : 'Confirmar mi correo' }}
              </button>

              <p class="centrado mt-2 mb-0" *ngIf="error()">
                <a routerLink="/registro" class="pequeno">Volver a solicitar la cuenta</a>
              </p>
            </ng-template>

          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .pagina {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 40px 20px;
      background:
        radial-gradient(900px 420px at 50% -8%, var(--verde-50), transparent 70%),
        var(--fondo);
    }
    .caja { width: 100%; max-width: 420px; }
  `],
})
export class VerificarRegistroComponent {

  private readonly auth = inject(AuthService);

  /** Llega de la ruta gracias a withComponentInputBinding(). */
  @Input() token = '';

  readonly enviando = signal(false);
  readonly confirmado = signal(false);
  readonly error = signal<string | null>(null);

  confirmar(): void {
    if (this.enviando()) {
      return;
    }

    this.enviando.set(true);
    this.error.set(null);

    this.auth.verificarRegistro(this.token).subscribe({
      next: () => {
        this.enviando.set(false);
        this.confirmado.set(true);
      },
      error: (err) => {
        this.enviando.set(false);
        this.error.set(AvisoComponent.mensajeDe(err, 'El enlace no es válido o ha caducado.'));
      },
    });
  }
}
