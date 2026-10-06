import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { AuthService } from '../services/auth.service';

/** Rutas que no necesitan sesión, y en las que un 401 es una respuesta normal. */
const RUTAS_PUBLICAS = [
  '/auth/login', '/auth/invitations', '/auth/forgot-password', '/auth/reset-password', '/health',
];

const esRutaPublica = (url: string) => RUTAS_PUBLICAS.some(ruta => url.includes(ruta));

/**
 * Añade el token a las peticiones y cierra la sesión cuando caduca.
 *
 * Las rutas públicas se comprueban contra una lista explícita. La versión
 * anterior lo hacía buscando fragmentos sueltos en la URL, y como el endpoint
 * de alta se llamaba "/registeruser" y el fragmento buscado era "/registro",
 * nunca casaban: al crear una cuenta el interceptor creía que la sesión había
 * caducado y echaba al usuario al login en mitad del registro.
 */
export const authInterceptor: HttpInterceptorFn = (peticion, siguiente) => {
  const auth = inject(AuthService);
  const token = auth.token;

  const conCabecera = token && !esRutaPublica(peticion.url)
    ? peticion.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : peticion;

  return siguiente(conCabecera).pipe(
    catchError((error: HttpErrorResponse) => {
      // Un 401 en una ruta protegida significa token caducado o inválido:
      // se cierra la sesión para no dejar la interfaz en un estado imposible
      if (error.status === 401 && !esRutaPublica(peticion.url)) {
        auth.logout();
      }
      return throwError(() => error);
    })
  );
};
