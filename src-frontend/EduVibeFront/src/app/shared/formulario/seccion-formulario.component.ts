import { Component, Input } from '@angular/core';

/**
 * Tarjeta con una parte del formulario, con su título pequeño en mayúsculas.
 *
 * Partir un formulario largo en secciones con nombre ("Datos básicos",
 * "Evaluación"...) deja ver de un vistazo qué hay y dónde, en lugar de una
 * columna única de campos sin pausa.
 */
@Component({
  selector: 'app-seccion-formulario',
  standalone: true,
  template: `
    <section class="tarjeta tarjeta-cuerpo seccion">
      <h2 class="titulo-seccion">{{ titulo }}</h2>
      <ng-content></ng-content>
    </section>
  `,
  styles: [`
    :host { display: block; margin-bottom: 16px; }
    .titulo-seccion {
      margin: 0 0 16px;
      font-size: .75rem;
      font-weight: 650;
      letter-spacing: .06em;
      text-transform: uppercase;
      color: var(--gris);
    }
  `],
})
export class SeccionFormularioComponent {
  @Input({ required: true }) titulo = '';
}
