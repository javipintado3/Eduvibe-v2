import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { EstadoSolicitud, Pagina, Rol, SolicitudRegistro, UsuarioCreado } from '../models';
import { TAMANO_PAGINA } from '../utils/paginacion';

/** Solicitudes de registro, vistas desde la administración. */
@Injectable({ providedIn: 'root' })
export class SolicitudesRegistroService {

  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/registration-requests`;

  listar(filtros: { status?: EstadoSolicitud; page?: number } = {}): Observable<Pagina<SolicitudRegistro>> {
    let params = new HttpParams()
      .set('page', filtros.page ?? 0)
      .set('size', TAMANO_PAGINA);
    if (filtros.status) params = params.set('status', filtros.status);

    return this.http.get<Pagina<SolicitudRegistro>>(this.api, { params });
  }

  /** Crea la cuenta con el rol elegido y devuelve su invitación. */
  aprobar(solicitudId: string, role: Rol): Observable<UsuarioCreado> {
    return this.http.post<UsuarioCreado>(`${this.api}/${solicitudId}/approve`, { role });
  }

  rechazar(solicitudId: string): Observable<void> {
    return this.http.post<void>(`${this.api}/${solicitudId}/reject`, {});
  }
}
