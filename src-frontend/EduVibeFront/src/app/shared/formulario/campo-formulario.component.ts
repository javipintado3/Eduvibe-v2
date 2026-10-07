import { Component, Input } from '@angular/core';
import { NgIf } from '@angular/common';

/**
 * Un campo del formulario: etiqueta encima (con asterisco si es obligatorio),
 * el control proyectado en medio y debajo el error o, si no lo hay, la ayuda.
 *
 * No sabe nada del control que envuelve: quien lo usa decide cuándo hay error
 * (normalmente cuando el campo ya se ha tocado) y lo pasa como texto. Así el
 * error no salta mientras la persona todavía está escribiendo.
 */
@Component({
  selector: 'app-campo',
  standalone: true,
  imports: [NgIf],
  template: `
    <div class="campo">
      <label class="etiqueta" [attr.for]="para">
        {{ etiqueta }}<span class="obligatorio" *ngIf="requerido" aria-hidden="true"> *</span>
      </label>
      <ng-content></ng-content>
      <span class="error-campo" *ngIf="error; else ayudaTpl">{{ error }}</span>
      <ng-template #ayudaTpl><span class="ayuda-campo" *ngIf="ayuda">{{ ayuda }}</span></ng-template>
    </div>
  `,
  styles: [`
    :host { display: block; margin-bottom: 16px; }
    :host(:last-child) { margin-bottom: 0; }
    .campo { margin-bottom: 0; }
    .obligatorio { color: var(--rojo); }
  `],
})
export class CampoFormularioComponent {

  @Input({ required: true }) etiqueta = '';
  /** id del control proyectado, para que pinchar la etiqueta lo enfoque. */
  @Input() para: string | null = null;
  @Input() requerido = false;
  @Input() ayuda = '';
  @Input() error: string | null = null;
}
