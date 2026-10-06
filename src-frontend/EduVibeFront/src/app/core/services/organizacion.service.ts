import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Organizacion } from '../models';

@Injectable({ providedIn: 'root' })
export class OrganizacionService {

  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/organization`;

  obtener(): Observable<Organizacion> {
    return this.http.get<Organizacion>(this.api);
  }

  /** "" quita la restricción de dominio. */
  cambiarDominioPermitido(allowedDomain: string): Observable<Organizacion> {
    return this.http.put<Organizacion>(`${this.api}/allowed-domain`, { allowedDomain });
  }
}
