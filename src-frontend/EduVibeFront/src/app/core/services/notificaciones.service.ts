import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Notificacion, Pagina } from '../models';
import { TAMANO_PAGINA } from '../utils/paginacion';

@Injectable({ providedIn: 'root' })
export class NotificacionesService {

  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/notifications`;

  /** Una página de las notificaciones de quien consulta, las más recientes primero. */
  misNotificaciones(pagina = 0, soloNoLeidas = false): Observable<Pagina<Notificacion>> {
    const params = new HttpParams().set('page', pagina).set('size', TAMANO_PAGINA).set('soloNoLeidas', soloNoLeidas);
    return this.http.get<Pagina<Notificacion>>(this.api, { params });
  }

  noLeidas(): Observable<number> {
    return this.http.get<{ count: number }>(`${this.api}/unread-count`)
      .pipe(map(respuesta => respuesta.count));
  }

  marcarLeida(notificacionId: string): Observable<void> {
    return this.http.put<void>(`${this.api}/${notificacionId}/read`, {});
  }

  marcarTodasLeidas(): Observable<void> {
    return this.http.put<void>(`${this.api}/read-all`, {});
  }
}
