import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { AuthService } from '../../core/services/auth.service';
import { TemaService } from '../../core/services/tema.service';
import { LogoComponent } from '../../shared/logo/logo.component';
import { AvatarComponent } from '../../shared/avatar/avatar.component';
import { NotificacionesComponent } from '../../shared/notificaciones/notificaciones.component';
import { PastillaEstadoComponent } from '../../shared/pastilla-estado/pastilla-estado.component';

interface EnlaceNavegacion {
  ruta: string;
  texto: string;
}

/**
 * Barra superior.
 *
 * Los enlaces que se muestran dependen del rol, y no solo por comodidad:
 * enseñar "Usuarios" a un alumno para que después reciba un 403 es una forma
 * de mentirle sobre lo que puede hacer.
 *
 * En la barra van solo los destinos de trabajo diario de cada rol. Lo que se
 * visita poco (perfil) o no es una sección (notificaciones, tema, cerrar
 * sesión) va a la derecha como icono o dentro del menú del avatar.
 */
@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [
    NgIf, NgFor, RouterLink, RouterLinkActive,
    LogoComponent, AvatarComponent, NotificacionesComponent, PastillaEstadoComponent,
  ],
  templateUrl: './navbar.component.html',
  styleUrl: './navbar.component.css',
})
export class NavbarComponent {

  readonly auth = inject(AuthService);
  readonly tema = inject(TemaService);

  /**
   * Secciones de la barra según el rol. El calendario no está para
   * administración: no es su vista de trabajo, y lo que necesite lo ve dentro
   * de Clases. El profesorado tiene "Calificar" (lo que le queda por corregir)
   * en lugar de "Calificaciones": la tabla completa vive dentro de cada clase.
   */
  readonly enlaces = computed<EnlaceNavegacion[]>(() => {
    if (this.auth.esAdmin()) {
      return [
        { ruta: '/admin/usuarios', texto: 'Usuarios' },
        { ruta: '/clases', texto: 'Clases' },
      ];
    }
    const comunes: EnlaceNavegacion[] = [
      { ruta: '/clases', texto: 'Mis clases' },
      { ruta: '/calendario', texto: 'Calendario' },
    ];
    if (this.auth.esProfesor()) {
      return [...comunes, { ruta: '/correcciones', texto: 'Calificar' }];
    }
    return this.auth.esAlumno() ? [...comunes, { ruta: '/notas', texto: 'Notas' }] : comunes;
  });

  readonly menuAbierto = signal(false);
  readonly navegacionAbierta = signal(false);

  alternarMenu(): void {
    this.menuAbierto.update(abierto => !abierto);
  }

  alternarNavegacion(): void {
    this.navegacionAbierta.update(abierta => !abierta);
  }

  cerrarTodo(): void {
    this.menuAbierto.set(false);
    this.navegacionAbierta.set(false);
  }

  salir(): void {
    this.cerrarTodo();
    this.auth.cerrarSesion();
  }

  /** Cierra el menú al pulsar en cualquier otro sitio de la página. */
  @HostListener('document:click', ['$event'])
  alPulsarFuera(evento: MouseEvent): void {
    const objetivo = evento.target as HTMLElement;
    if (!objetivo.closest('[data-menu-usuario]')) {
      this.menuAbierto.set(false);
    }
  }
}
