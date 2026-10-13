import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { retry, takeWhile, timeout, timer } from 'rxjs';

import { environment } from '../../../environments/environment';

/** Si la API responde antes de esto, el visitante no llega a ver ningún aviso. */
const SEGUNDOS_HASTA_AVISAR = 1;

/**
 * Sabe si la API está despierta.
 *
 * En el alojamiento gratuito el servidor se duerme cuando nadie lo visita y
 * tarda hasta un minuto en volver. La web (que se sirve aparte) carga al
 * instante, así que sin esto el visitante vería una pantalla que parece rota.
 *
 * Al arrancar llama a /health, que es público y barato, y no para hasta que
 * responde: cada intento tiene su propio tiempo máximo y se reintenta solo.
 * El tiempo transcurrido se cuenta aquí para que la pantalla de espera pueda
 * ir marcando pasos sin tener que llevar su propio reloj.
 */
@Injectable({ providedIn: 'root' })
export class ServidorService {

  private readonly http = inject(HttpClient);

  readonly despierto = signal(false);
  readonly segundos = signal(0);

  /** Solo es true si la espera es lo bastante larga como para merecer un aviso. */
  readonly tardando = computed(() => !this.despierto() && this.segundos() >= SEGUNDOS_HASTA_AVISAR);

  constructor() {
    timer(0, 1000)
      .pipe(takeWhile(() => !this.despierto()))
      .subscribe(segundos => this.segundos.set(segundos));

    this.http.get(`${environment.apiUrl}/health`)
      .pipe(timeout(15000), retry({ delay: 3000 }))
      .subscribe(() => this.despierto.set(true));
  }
}
