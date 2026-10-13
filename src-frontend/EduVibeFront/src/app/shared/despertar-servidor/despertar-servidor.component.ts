import { Component, inject } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';

import { ServidorService } from '../../core/services/servidor.service';
import { LogoComponent } from '../logo/logo.component';

/** Lo que se enseña mientras el servidor despierta: la espera sirve para conocer la app. */
const NOVEDADES = [
  { icono: '📚', titulo: 'Clases y tareas', texto: 'Materiales, entregas y calificación con rúbricas.' },
  { icono: '📝', titulo: 'Exámenes', texto: 'Banco de preguntas y corrección automática.' },
  { icono: '💬', titulo: 'Foros y analítica', texto: 'Debate entre alumnos y datos para el profesorado.' },
];

/** Pasos aproximados: se marcan según el tiempo, no según lo que haga el servidor por dentro. */
const PASOS = ['Conectando con el servidor', 'Despertando el servidor', 'Arrancando la aplicación y cargando la demo'];

/**
 * Pantalla de espera para cuando el servidor gratuito está dormido.
 *
 * Solo aparece si la API tarda más de un segundo en responder; con el servidor
 * despierto el visitante no la ve nunca. Tapa la interfaz entera a propósito:
 * hasta que la API responda, cualquier botón que pulsara se quedaría colgado.
 */
@Component({
  selector: 'app-despertar-servidor',
  standalone: true,
  imports: [NgIf, NgFor, LogoComponent],
  template: `
    <div class="pantalla" *ngIf="servidor.tardando()" role="status" aria-live="polite">
      <div class="contenido">
        <app-logo [size]="44"></app-logo>

        <h1>Despertando el servidor…</h1>
        <p class="explicacion">
          EduVibe está alojado en un servidor gratuito que se duerme cuando no recibe visitas.
          Está arrancando y tarda <strong>menos de un minuto</strong>. No hace falta que recargues la página.
        </p>

        <ol class="pasos">
          <li *ngFor="let paso of pasos; let i = index" [class.hecho]="i < pasoActual()" [class.actual]="i === pasoActual()">
            <span class="marca">
              <span class="cargando" *ngIf="i === pasoActual()"></span>
              <span *ngIf="i < pasoActual()">✓</span>
            </span>
            {{ paso }}
          </li>
        </ol>

        <p class="aprovecha apagado pequeno">Mientras tanto, esto es lo que encontrarás dentro:</p>
        <div class="tarjetas">
          <div class="tarjeta" *ngFor="let novedad of novedades">
            <span class="icono" aria-hidden="true">{{ novedad.icono }}</span>
            <strong>{{ novedad.titulo }}</strong>
            <span class="apagado pequeno">{{ novedad.texto }}</span>
          </div>
        </div>

        <p class="apagado pequeno">{{ servidor.segundos() }} s</p>
      </div>
    </div>
  `,
  styles: [`
    .pantalla {
      position: fixed;
      inset: 0;
      z-index: 1000;
      display: flex;
      align-items: flex-start;
      justify-content: center;
      overflow-y: auto;
      padding: 32px 16px;
      background: var(--fondo);
      animation: aparecer .3s ease-out;
    }

    .contenido {
      width: 100%;
      max-width: 640px;
      margin: auto 0;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 16px;
      text-align: center;
    }

    h1 { margin: 8px 0 0; font-size: 1.5rem; }

    .explicacion { margin: 0; max-width: 480px; color: var(--tinta-70); line-height: 1.5; }

    .pasos {
      list-style: none;
      margin: 8px 0;
      padding: 16px 20px;
      width: 100%;
      max-width: 420px;
      display: flex;
      flex-direction: column;
      gap: 10px;
      text-align: left;
      background: var(--blanco);
      border: 1px solid var(--borde);
      border-radius: var(--radio);
      box-shadow: var(--sombra);
    }

    .pasos li { display: flex; align-items: center; gap: 12px; color: var(--gris); }
    .pasos li.actual { color: var(--tinta); font-weight: 600; }
    .pasos li.hecho { color: var(--verde-700); }

    .marca { width: 20px; display: inline-flex; justify-content: center; flex-shrink: 0; }

    .aprovecha { margin: 8px 0 0; }

    .tarjetas {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 12px;
      width: 100%;
    }

    .tarjeta {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 6px;
      padding: 16px 12px;
      background: var(--blanco);
      border: 1px solid var(--borde);
      border-radius: var(--radio);
      box-shadow: var(--sombra-sm);
    }

    .icono { font-size: 1.6rem; line-height: 1; }

    @media (max-width: 560px) {
      .tarjetas { grid-template-columns: 1fr; }
      .tarjeta { flex-direction: row; text-align: left; gap: 12px; }
      .tarjeta strong { white-space: nowrap; }
    }

    @keyframes aparecer { from { opacity: 0; } to { opacity: 1; } }

    @media (prefers-reduced-motion: reduce) {
      .pantalla { animation: none; }
    }
  `]
})
export class DespertarServidorComponent {

  protected readonly servidor = inject(ServidorService);

  protected readonly pasos = PASOS;
  protected readonly novedades = NOVEDADES;

  /** Índice del paso en curso: cambia con el tiempo, a las 5 y a las 25 segundos. */
  protected readonly pasoActual = () => {
    const segundos = this.servidor.segundos();
    return segundos < 5 ? 0 : segundos < 25 ? 1 : 2;
  };
}
