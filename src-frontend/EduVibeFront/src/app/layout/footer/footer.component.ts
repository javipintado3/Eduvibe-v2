import { Component } from '@angular/core';

import { VERSION_APP } from '../../core/version';
import { LogoComponent } from '../../shared/logo/logo.component';

@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [LogoComponent],
  template: `
    <footer class="pie">
      <div class="contenedor fila-entre envolver espaciado">
        <app-logo [size]="22"></app-logo>
        <p class="pequeno apagado mb-0">
          Plataforma educativa · {{ anio }} · v{{ version }}
        </p>
      </div>
    </footer>
  `,
  styles: [`
    .pie {
      border-top: 1px solid var(--borde);
      background: var(--blanco);
      padding: 20px 0;
      margin-top: 56px;
    }
  `]
})
export class FooterComponent {
  readonly anio = new Date().getFullYear();
  readonly version = VERSION_APP;
}
