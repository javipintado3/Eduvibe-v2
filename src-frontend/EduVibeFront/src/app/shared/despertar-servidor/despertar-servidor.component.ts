import { Component, computed, inject } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';

import { ServidorService } from '../../core/services/servidor.service';
import { LogoComponent } from '../logo/logo.component';

/** Lo que se enseña de fondo mientras el servidor despierta: la espera sirve para conocer la app. */
const DIAPOSITIVAS = [
  { imagen: 'assets/carga/mis-clases.jpg', etiqueta: 'Clases', titulo: 'Todas tus clases en un solo sitio',
    texto: 'Portadas, próximas entregas y avisos de un vistazo.' },
  { imagen: 'assets/carga/unidades.jpg', etiqueta: 'Contenido', titulo: 'Cada clase, organizada por unidades',
    texto: 'Materiales, tareas y exámenes agrupados para que nadie se pierda.' },
  { imagen: 'assets/carga/entregas.jpg', etiqueta: 'Tareas', titulo: 'Entregas, notas y rúbricas',
    texto: 'Corrige con una nota o con rúbrica y deja comentarios al alumnado.' },
  { imagen: 'assets/carga/examenes.jpg', etiqueta: 'Exámenes', titulo: 'Exámenes con corrección automática',
    texto: 'Banco de preguntas, tiempo limitado y nota al instante.' },
  { imagen: 'assets/carga/foro.jpg', etiqueta: 'Foros', titulo: 'Dudas resueltas entre todos',
    texto: 'Hilos de debate por clase, con profesorado y alumnado.' },
  { imagen: 'assets/carga/analitica.jpg', etiqueta: 'Analítica', titulo: 'Detecta al alumnado en riesgo',
    texto: 'Entregas, medias y progreso de cada persona de la clase.' },
  { imagen: 'assets/carga/calendario.jpg', etiqueta: 'Calendario', titulo: 'Fechas de entrega y eventos',
    texto: 'Todo lo que vence, en una vista mensual.' },
];

/** Cada cuántos segundos cambia la imagen de fondo. */
const SEGUNDOS_POR_IMAGEN = 6;

/** Pasos aproximados: se marcan según el tiempo, no según lo que haga el servidor por dentro. */
const PASOS = ['Conectando con el servidor', 'Despertando el servidor', 'Arrancando la aplicación y cargando la demo'];

/**
 * Pantalla de espera para cuando el servidor gratuito está dormido.
 *
 * Solo aparece si la API tarda más de un segundo en responder; con el servidor
 * despierto el visitante no la ve nunca. Tapa la interfaz entera a propósito:
 * hasta que la API responda, cualquier botón que pulsara se quedaría colgado.
 *
 * Como las pantallas de carga de los videojuegos, el fondo va pasando capturas
 * de la aplicación, cada una con una frase. La imagen en curso se deduce del
 * tiempo transcurrido que ya cuenta el servicio, así que no hay un segundo
 * reloj: cambiar de imagen es solo una cuenta.
 */
@Component({
  selector: 'app-despertar-servidor',
  standalone: true,
  imports: [NgIf, NgFor, LogoComponent],
  template: `
    <div class="pantalla" *ngIf="servidor.tardando()" role="status" aria-live="polite">

      <div class="fondo" aria-hidden="true">
        <img *ngFor="let diapositiva of diapositivas; let i = index" class="foto" alt="" decoding="async"
             [src]="diapositiva.imagen"
             [class.activa]="i === indice()" [class.saliendo]="i === anterior()">
      </div>
      <div class="velo" aria-hidden="true"></div>

      <div class="marca"><app-logo [size]="34"></app-logo></div>

      <div class="contenido">
        <section class="estado">
          <h1>Despertando el servidor…</h1>
          <p class="explicacion">
            EduVibe está alojado en un servidor gratuito que se duerme cuando no recibe visitas.
            Está arrancando y puede tardar <strong>un par de minutos</strong>. No hace falta que recargues la página.
          </p>

          <ol class="pasos">
            <li *ngFor="let paso of pasos; let i = index" [class.hecho]="i < pasoActual()" [class.actual]="i === pasoActual()">
              <span class="marca-paso">
                <span class="cargando" *ngIf="i === pasoActual()"></span>
                <span *ngIf="i < pasoActual()">✓</span>
              </span>
              {{ paso }}
            </li>
          </ol>
        </section>

        <section class="pie-diapositiva" aria-hidden="true">
          <span class="etiqueta">{{ actual().etiqueta }}</span>
          <h2>{{ actual().titulo }}</h2>
          <p>{{ actual().texto }}</p>
          <div class="puntos">
            <span class="punto" *ngFor="let diapositiva of diapositivas; let i = index" [class.activo]="i === indice()"></span>
          </div>
        </section>
      </div>

      <div class="barra" aria-hidden="true"><span></span></div>
      <p class="segundos">{{ servidor.segundos() }} s</p>
    </div>
  `,
  styles: [`
    .pantalla {
      position: fixed;
      inset: 0;
      z-index: 1000;
      display: flex;
      flex-direction: column;
      overflow: hidden;
      background: #020617;
      color: #f8fafc;
      animation: aparecer .3s ease-out;
    }

    /* ---- Fondo: capturas que se funden y se acercan despacio ---- */
    .fondo, .velo { position: absolute; inset: 0; }

    .foto {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
      object-fit: cover;
      object-position: 30% center;
      opacity: 0;
      transition: opacity 1.2s ease;
    }

    .foto.activa { opacity: 1; }

    /* La que se va sigue acercándose mientras se funde, para que no dé un salto */
    .foto.activa, .foto.saliendo { animation: acercar 14s linear forwards; }

    .velo {
      background:
        linear-gradient(180deg, rgba(2, 6, 23, .4) 0%, rgba(2, 6, 23, .05) 30%, rgba(2, 6, 23, .6) 62%, rgba(2, 6, 23, .94) 100%);
    }

    /* ---- Marca ---- */
    .marca {
      position: relative;
      align-self: flex-start;
      margin: 20px 24px 0;
      padding: 8px 14px;
      background: #fff;
      /* La etiqueta es blanca en cualquier tema, así que el logo usa siempre los colores del tema claro */
      --tinta: #0f172a;
      --verde-600: #059669;
      border-radius: 12px;
      box-shadow: 0 4px 16px rgba(0, 0, 0, .25);
    }

    /* ---- Contenido, abajo, como en las pantallas de carga de un juego ---- */
    .contenido {
      position: relative;
      flex: 1;
      display: flex;
      align-items: flex-end;
      justify-content: space-between;
      gap: 32px;
      padding: 24px 24px 56px;
    }

    .estado { max-width: 460px; }

    h1 { margin: 0 0 8px; font-size: 1.75rem; color: #fff; }

    .explicacion { margin: 0 0 16px; color: #cbd5e1; line-height: 1.5; font-size: .95rem; }
    .explicacion strong { color: #fff; }

    .pasos {
      list-style: none;
      margin: 0;
      padding: 14px 18px;
      display: flex;
      flex-direction: column;
      gap: 8px;
      background: rgba(15, 23, 42, .6);
      border: 1px solid rgba(255, 255, 255, .14);
      border-radius: var(--radio);
      backdrop-filter: blur(6px);
    }

    .pasos li { display: flex; align-items: center; gap: 12px; color: #94a3b8; font-size: .9rem; }
    .pasos li.actual { color: #fff; font-weight: 600; }
    .pasos li.hecho { color: #6ee7b7; }

    .marca-paso { width: 20px; display: inline-flex; justify-content: center; flex-shrink: 0; }

    /* ---- Pie de la diapositiva en curso ---- */
    .pie-diapositiva { max-width: 380px; text-align: right; }

    .etiqueta {
      display: inline-block;
      padding: 3px 10px;
      border-radius: 999px;
      background: #10b981;
      color: #022c22;
      font-size: .72rem;
      font-weight: 700;
      letter-spacing: .06em;
      text-transform: uppercase;
    }

    h2 { margin: 10px 0 4px; font-size: 1.5rem; line-height: 1.2; color: #fff; text-shadow: 0 2px 12px rgba(0, 0, 0, .5); }
    .pie-diapositiva p { margin: 0 0 14px; color: #e2e8f0; text-shadow: 0 1px 8px rgba(0, 0, 0, .5); }

    .puntos { display: flex; justify-content: flex-end; gap: 6px; }
    .punto { width: 7px; height: 7px; border-radius: 50%; background: rgba(255, 255, 255, .35); transition: all .3s; }
    .punto.activo { width: 22px; border-radius: 4px; background: #34d399; }

    /* ---- Barra indeterminada: no se sabe cuánto falta, así que no se finge ---- */
    .barra { position: absolute; left: 0; right: 0; bottom: 0; height: 4px; background: rgba(255, 255, 255, .12); overflow: hidden; }
    .barra span {
      position: absolute;
      top: 0;
      bottom: 0;
      width: 30%;
      background: linear-gradient(90deg, transparent, #34d399, transparent);
      animation: recorrer 1.8s ease-in-out infinite;
    }

    .segundos { position: absolute; right: 24px; bottom: 14px; margin: 0; font-size: .78rem; color: #94a3b8; }

    @keyframes aparecer { from { opacity: 0; } to { opacity: 1; } }
    @keyframes acercar { from { transform: scale(1); } to { transform: scale(1.1); } }
    @keyframes recorrer { from { left: -30%; } to { left: 100%; } }

    @media (max-width: 760px) {
      .contenido { flex-direction: column; align-items: stretch; justify-content: flex-end; gap: 20px; padding-bottom: 48px; }
      .estado, .pie-diapositiva { max-width: none; }
      .pie-diapositiva { text-align: left; order: -1; }
      .puntos { justify-content: flex-start; }
      h1 { font-size: 1.4rem; }
      h2 { font-size: 1.25rem; }
      .foto { object-position: 25% center; }
      /* Con el texto ocupando media pantalla, el fondo tiene que ceder más */
      .velo { background: linear-gradient(180deg, rgba(2, 6, 23, .55) 0%, rgba(2, 6, 23, .7) 40%, rgba(2, 6, 23, .94) 100%); }
    }

    @media (prefers-reduced-motion: reduce) {
      .pantalla { animation: none; }
      .foto.activa, .foto.saliendo { animation: none; }
      .barra span { animation: none; left: 0; width: 100%; opacity: .5; }
    }
  `]
})
export class DespertarServidorComponent {

  protected readonly servidor = inject(ServidorService);

  protected readonly diapositivas = DIAPOSITIVAS;
  protected readonly pasos = PASOS;

  /** Imagen en curso: sube una cada pocos segundos y da la vuelta al llegar a la última. */
  protected readonly indice = computed(
    () => Math.floor(this.servidor.segundos() / SEGUNDOS_POR_IMAGEN) % DIAPOSITIVAS.length);

  /** La anterior, que sigue visible un momento mientras se funde. */
  protected readonly anterior = computed(
    () => (this.indice() + DIAPOSITIVAS.length - 1) % DIAPOSITIVAS.length);

  protected readonly actual = computed(() => DIAPOSITIVAS[this.indice()]);

  /** Índice del paso en curso: cambia con el tiempo, a los 8 y a los 45 segundos. */
  protected readonly pasoActual = () => {
    const segundos = this.servidor.segundos();
    return segundos < 8 ? 0 : segundos < 45 ? 1 : 2;
  };
}
