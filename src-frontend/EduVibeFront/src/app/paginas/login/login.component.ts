import { Component, inject, signal } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/services/auth.service';
import { AvisoComponent } from '../../shared/aviso/aviso.component';
import { LogoComponent } from '../../shared/logo/logo.component';

/** Una cuenta de demostración que se puede probar con un clic. */
interface CuentaDemo {
  rol: string;
  email: string;
  descripcion: string;
}

/**
 * Pantalla de acceso.
 *
 * No hay enlace de "crear cuenta": las altas las hace un administrador y se
 * activan con una invitación.
 *
 * Sí hay, en cambio, credenciales de demostración a la vista. Es deliberado:
 * sin registro público, quien abra la aplicación desde un portafolio no
 * tendría forma de entrar, y una demo en la que no se puede entrar no
 * demuestra nada.
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [NgIf, NgFor, ReactiveFormsModule, RouterLink, AvisoComponent, LogoComponent],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
})
export class LoginComponent {

  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly ruta = inject(ActivatedRoute);

  readonly formulario = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
    // Solo se pide cuando el servidor avisa de que la cuenta tiene 2FA
    codigo: [''],
  });

  /** El servidor ha dicho que falta el código de verificación en dos pasos. */
  readonly pedirCodigo = signal(false);

  readonly enviando = signal(false);
  readonly error = signal<string | null>(null);

  readonly cuentasDemo: CuentaDemo[] = [
    { rol: 'Administración', email: 'admin@eduvibe.demo', descripcion: 'Gestiona usuarios y clases' },
    { rol: 'Profesorado', email: 'ivan.cordero@eduvibe.demo', descripcion: 'Pone tareas y corrige' },
    { rol: 'Alumnado', email: 'abril.medina@eduvibe.demo', descripcion: 'Entrega y consulta notas' },
  ];

  readonly contrasenaDemo = 'demo1234';

  campoInvalido(nombre: 'email' | 'password'): boolean {
    const campo = this.formulario.controls[nombre];
    return campo.invalid && campo.touched;
  }

  usarCuenta(cuenta: CuentaDemo): void {
    this.formulario.setValue({ email: cuenta.email, password: this.contrasenaDemo, codigo: '' });
    this.pedirCodigo.set(false);
    this.error.set(null);
  }

  entrar(): void {
    this.formulario.markAllAsTouched();

    if (this.formulario.invalid || this.enviando()) {
      return;
    }

    this.enviando.set(true);
    this.error.set(null);

    const { email, password, codigo } = this.formulario.getRawValue();

    this.auth.login(email, password, codigo.trim() || undefined).subscribe({
      next: () => {
        // Si el guard mandó aquí desde otra pantalla, se vuelve a ella
        const destino = this.ruta.snapshot.queryParamMap.get('volverA') || '/clases';
        this.router.navigateByUrl(destino);
      },
      error: (err) => {
        this.enviando.set(false);

        // 428: la contraseña es correcta pero falta el código de la app de
        // autenticación. No es un error: se muestra el campo y se vuelve a enviar
        if (err instanceof HttpErrorResponse && err.status === 428) {
          this.pedirCodigo.set(true);
          return;
        }
        this.error.set(AvisoComponent.mensajeDe(err, 'Usuario y/o contraseña incorrectos'));
      },
    });
  }
}
