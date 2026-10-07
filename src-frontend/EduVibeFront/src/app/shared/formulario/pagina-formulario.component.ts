import { Component, Input } from '@angular/core';
import { NgIf } from '@angular/common';
import { RouterLink } from '@angular/router';

import { AvisoComponent } from '../aviso/aviso.component';

/**
 * Esqueleto de una página de alta o edición.
 *
 * Reúne lo que tienen en común todas: título con un "Volver", las secciones
 * proyectadas en el centro y una barra de acciones fija abajo, con el error del
 * envío encima. Así guardar y cancelar siempre están a la vista, por largo que
 * sea el formulario, y cada pantalla solo aporta sus secciones.
 *
 * El botón de guardar envía el formulario cuyo id se indica en `formId`
 * (atributo `form` del botón), de modo que la barra puede vivir fuera del <form>.
 */
@Component({
  selector: 'app-pagina-formulario',
  standalone: true,
  imports: [NgIf, RouterLink, AvisoComponent],
  template: `
    <div class="pagina">
      <div class="cabecera">
        <a class="boton boton-contorno boton-pequeno" [routerLink]="rutaVolver">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
          Volver
        </a>
        <h1>{{ titulo }}</h1>
        <p class="apagado mb-0" *ngIf="descripcion">{{ descripcion }}</p>
      </div>

      <ng-content></ng-content>
    </div>

    <div class="barra">
      <div class="barra-contenido">
        <app-aviso class="mb-2" tipo="error" [mensaje]="error"></app-aviso>
        <div class="acciones">
          <a class="boton boton-contorno" [routerLink]="rutaVolver">Cancelar</a>
          <button type="submit" class="boton boton-primario" [attr.form]="formId" [disabled]="guardando">
            {{ guardando ? textoGuardando : textoGuardar }}
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; }

    .pagina { width: 100%; max-width: 720px; margin: 0 auto; padding: 0 20px 24px; }
    .cabecera { margin: 24px 0; }
    .cabecera h1 { margin: 14px 0 4px; }

    /* Fija abajo; el fondo tapa lo que pasa por detrás al hacer scroll */
    .barra {
      position: sticky;
      bottom: 0;
      z-index: 5;
      background: var(--blanco);
      border-top: 1px solid var(--borde);
    }
    .barra-contenido { width: 100%; max-width: 720px; margin: 0 auto; padding: 14px 20px; }
    .acciones { display: flex; justify-content: flex-end; gap: 10px; }
  `],
})
export class PaginaFormularioComponent {

  @Input({ required: true }) titulo = '';
  @Input() descripcion = '';
  @Input({ required: true }) formId = '';
  /** A dónde lleva "Volver" y "Cancelar". */
  @Input({ required: true }) rutaVolver: string | unknown[] = '/';
  @Input() error: string | null = null;
  @Input() guardando = false;
  @Input() textoGuardar = 'Guardar';
  @Input() textoGuardando = 'Guardando…';
}
