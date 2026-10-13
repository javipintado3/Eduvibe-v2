import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { TemaService } from './core/services/tema.service';
import { DespertarServidorComponent } from './shared/despertar-servidor/despertar-servidor.component';

/**
 * Raíz de la aplicación.
 *
 * No pinta nada por su cuenta: la barra y el pie viven en
 * LayoutPrincipalComponent, que es una ruta, para que las pantallas sin sesión
 * puedan quedarse fuera de ese armazón.
 *
 * Lleva encima la pantalla de espera del servidor, que solo se ve si la API
 * (en el alojamiento gratuito) está dormida y tarda en responder.
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, DespertarServidorComponent],
  template: '<app-despertar-servidor></app-despertar-servidor><router-outlet></router-outlet>',
})
export class AppComponent {

  // Se inyecta aquí, aunque nadie lea `tema`, para que el servicio (y su
  // efecto que aplica data-tema) se instancie al arrancar la app entera,
  // no solo cuando algún componente concreto lo pida.
  private readonly tema = inject(TemaService);
}
