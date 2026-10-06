import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { DatosInvitacion, RespuestaAutenticacion, Usuario } from '../models';

const CLAVE_TOKEN = 'eduvibe.token';
const CLAVE_USUARIO = 'eduvibe.usuario';

/**
 * Sesión del usuario.
 *
 * El estado vive en señales: cualquier componente que lea usuario() se vuelve
 * a pintar solo cuando cambia, sin suscripciones que recordar cancelar.
 *
 * La sesión se guarda en localStorage para sobrevivir a una recarga. Todos los
 * accesos van envueltos, porque en modo privado o con las cookies bloqueadas
 * el navegador lanza una excepción en lugar de devolver null, y eso dejaba la
 * aplicación entera inservible en la versión anterior.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {

  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly api = environment.apiUrl;

  private readonly _usuario = signal<Usuario | null>(this.leerUsuarioGuardado());

  /** Usuario de la sesión actual, o null si no hay ninguna. */
  readonly usuario = this._usuario.asReadonly();

  readonly estaAutenticado = computed(() => this._usuario() !== null);
  readonly esAdmin = computed(() => this._usuario()?.role === 'admin');
  readonly esProfesor = computed(() => this._usuario()?.role === 'teacher');
  readonly esAlumno = computed(() => this._usuario()?.role === 'student');

  /** Iniciales del nombre, para el avatar. */
  readonly iniciales = computed(() => {
    const nombre = this._usuario()?.name ?? '';
    return nombre
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map(parte => parte[0]!.toUpperCase())
      .join('');
  });

  login(email: string, password: string): Observable<RespuestaAutenticacion> {
    return this.http.post<RespuestaAutenticacion>(`${this.api}/auth/login`, { email, password })
      .pipe(tap(respuesta => this.guardarSesion(respuesta)));
  }

  /** Comprueba un enlace de invitación antes de pedir la contraseña. */
  consultarInvitacion(token: string): Observable<DatosInvitacion> {
    return this.http.get<DatosInvitacion>(`${this.api}/auth/invitations/${token}`);
  }

  /** Establece la contraseña, activa la cuenta y deja la sesión iniciada. */
  aceptarInvitacion(token: string, password: string): Observable<RespuestaAutenticacion> {
    return this.http.post<RespuestaAutenticacion>(
      `${this.api}/auth/invitations/${token}/accept`, { password })
      .pipe(tap(respuesta => this.guardarSesion(respuesta)));
  }

  /** Pide un enlace para elegir otra contraseña. Responde igual exista o no el email. */
  olvideMiContrasena(email: string): Observable<void> {
    return this.http.post<void>(`${this.api}/auth/forgot-password`, { email });
  }

  /** Establece la contraseña nueva con el enlace recibido por correo. */
  restablecerContrasena(token: string, password: string): Observable<void> {
    return this.http.post<void>(`${this.api}/auth/reset-password/${token}`, { password });
  }

  /** Relee el usuario del servidor; sirve para detectar un token ya caducado. */
  refrescarUsuario(): Observable<Usuario> {
    return this.http.get<Usuario>(`${this.api}/auth/me`)
      .pipe(tap(usuario => {
        this._usuario.set(usuario);
        this.escribir(CLAVE_USUARIO, JSON.stringify(usuario));
      }));
  }

  logout(destino = '/login'): void {
    this.borrar(CLAVE_TOKEN);
    this.borrar(CLAVE_USUARIO);
    this._usuario.set(null);
    this.router.navigateByUrl(destino);
  }

  get token(): string | null {
    return this.leer(CLAVE_TOKEN);
  }

  private guardarSesion(respuesta: RespuestaAutenticacion): void {
    this.escribir(CLAVE_TOKEN, respuesta.token);
    this.escribir(CLAVE_USUARIO, JSON.stringify(respuesta.user));
    this._usuario.set(respuesta.user);
  }

  private leerUsuarioGuardado(): Usuario | null {
    const guardado = this.leer(CLAVE_USUARIO);
    if (!guardado || !this.leer(CLAVE_TOKEN)) {
      return null;
    }
    try {
      return JSON.parse(guardado) as Usuario;
    } catch {
      // Dato corrupto de una versión anterior: se descarta en lugar de
      // propagar la excepción, que dejaría la aplicación sin arrancar
      this.borrar(CLAVE_USUARIO);
      return null;
    }
  }

  private leer(clave: string): string | null {
    try {
      return localStorage.getItem(clave);
    } catch {
      return null;
    }
  }

  private escribir(clave: string, valor: string): void {
    try {
      localStorage.setItem(clave, valor);
    } catch {
      // Sin almacenamiento la sesión no sobrevive a una recarga, pero la
      // aplicación sigue funcionando mientras la pestaña esté abierta
    }
  }

  private borrar(clave: string): void {
    try {
      localStorage.removeItem(clave);
    } catch {
      /* mismo caso que en escribir() */
    }
  }
}
