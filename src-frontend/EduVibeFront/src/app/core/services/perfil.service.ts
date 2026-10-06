import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ResumenPerfil, Usuario } from '../models';

@Injectable({ providedIn: 'root' })
export class PerfilService {

  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/profile`;

  resumen(): Observable<ResumenPerfil> {
    return this.http.get<ResumenPerfil>(`${this.api}/summary`);
  }

  /** "" (o sin argumento) quita la foto: se vuelve a las iniciales. */
  actualizarAvatar(url = ''): Observable<Usuario> {
    return this.http.put<Usuario>(`${this.api}/avatar`, { url });
  }

  /** Todos los datos de la persona, como archivo JSON (derecho de acceso y portabilidad). */
  exportarDatos(): Observable<Blob> {
    return this.http.get(`${this.api}/export`, { responseType: 'blob' });
  }

  /**
   * Borra la cuenta conservando solo el expediente. `code` solo hace falta si la
   * cuenta tiene la verificación en dos pasos.
   */
  borrarCuenta(password: string, code?: string): Observable<void> {
    return this.http.post<void>(`${this.api}/erase`, { password, code });
  }
}
