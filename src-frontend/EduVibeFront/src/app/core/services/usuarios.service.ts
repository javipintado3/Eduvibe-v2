import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ClaseDeUsuario, EstadoCuenta, Invitacion, Pagina, ResultadoImportacion, Rol, Usuario, UsuarioCreado } from '../models';
import { TAMANO_PAGINA } from '../utils/paginacion';

/** Panel de administración de usuarios. */
@Injectable({ providedIn: 'root' })
export class UsuariosService {

  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/users`;

  listar(filtros: {
    role?: Rol | '';
    status?: EstadoCuenta | '';
    q?: string;
    /** Deja fuera a quien ya está matriculado en esa clase; para el selector de "Añadir a la clase". */
    excludeClassId?: string;
    page?: number;
    size?: number;
    /** Orden de la API, "campo,asc" o "campo,desc". Sin él, por nombre. */
    sort?: string;
  } = {}): Observable<Pagina<Usuario>> {

    // Un filtro vacío no se envía: la API entiende la ausencia como "todos"
    let params = new HttpParams();
    if (filtros.role) params = params.set('role', filtros.role);
    if (filtros.status) params = params.set('status', filtros.status);
    if (filtros.q?.trim()) params = params.set('q', filtros.q.trim());
    if (filtros.excludeClassId) params = params.set('excludeClassId', filtros.excludeClassId);
    if (filtros.sort) params = params.set('sort', filtros.sort);
    params = params.set('page', filtros.page ?? 0).set('size', filtros.size ?? TAMANO_PAGINA);

    return this.http.get<Pagina<Usuario>>(this.api, { params });
  }

  crear(datos: { name: string; email: string; role: Rol }): Observable<UsuarioCreado> {
    return this.http.post<UsuarioCreado>(this.api, datos);
  }

  obtener(usuarioId: string): Observable<Usuario> {
    return this.http.get<Usuario>(`${this.api}/${usuarioId}`);
  }

  actualizar(usuarioId: string, datos: { name: string; email: string; role: Rol }): Observable<Usuario> {
    return this.http.put<Usuario>(`${this.api}/${usuarioId}`, datos);
  }

  /** Las clases en las que participa, para su ficha de administración. */
  clasesDe(usuarioId: string): Observable<ClaseDeUsuario[]> {
    return this.http.get<ClaseDeUsuario[]>(`${this.api}/${usuarioId}/classes`);
  }

  cambiarEstado(usuarioId: string, status: 'active' | 'disabled'): Observable<Usuario> {
    return this.http.patch<Usuario>(`${this.api}/${usuarioId}/status`, { status });
  }

  reenviarInvitacion(usuarioId: string): Observable<Invitacion> {
    return this.http.post<Invitacion>(`${this.api}/${usuarioId}/invitation`, {});
  }

  /** El CSV del listado filtrado (sin paginar), listo para descargar. */
  exportarCsv(filtros: { role?: Rol | ''; status?: EstadoCuenta | ''; q?: string } = {}): Observable<Blob> {
    let params = new HttpParams();
    if (filtros.role) params = params.set('role', filtros.role);
    if (filtros.status) params = params.set('status', filtros.status);
    if (filtros.q?.trim()) params = params.set('q', filtros.q.trim());

    return this.http.get(`${this.api}/export`, { params, responseType: 'blob' });
  }

  /** Alta masiva desde un CSV con columnas name, email, role. */
  importar(fichero: File): Observable<ResultadoImportacion> {
    const datos = new FormData();
    datos.append('file', fichero);
    return this.http.post<ResultadoImportacion>(`${this.api}/import`, datos);
  }
}
