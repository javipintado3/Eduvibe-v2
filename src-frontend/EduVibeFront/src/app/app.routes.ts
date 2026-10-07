import { Routes } from '@angular/router';

import { LayoutPrincipalComponent } from './layout/layout-principal/layout-principal.component';
import { adminGuard, alumnoGuard, invitadoGuard, profesorGuard, sesionGuard } from './core/guards/sesion.guard';

/**
 * Rutas de la aplicación.
 *
 * Dos zonas bien separadas:
 *
 *  - Fuera del layout: login e invitación. No llevan barra de navegación,
 *    porque quien las ve todavía no tiene sesión y no habría nada que navegar.
 *
 *  - Dentro de LayoutPrincipalComponent: el resto. El guard de sesión se
 *    declara una sola vez, en la ruta padre, y protege a todos los hijos; así
 *    añadir una pantalla nueva no puede dejarla desprotegida por olvido.
 *
 * Todas las páginas se cargan con loadComponent: cada una viaja en su propio
 * fragmento y el navegador solo descarga la que hace falta.
 */
export const routes: Routes = [

  // ------------------------------------------------------------ sin sesión
  {
    path: 'login',
    canActivate: [invitadoGuard],
    title: 'Entrar · Eduvibe',
    loadComponent: () => import('./paginas/login/login.component')
      .then(m => m.LoginComponent),
  },
  {
    path: 'olvide-contrasena',
    canActivate: [invitadoGuard],
    title: 'Recuperar contraseña · Eduvibe',
    loadComponent: () => import('./paginas/olvide-contrasena/olvide-contrasena.component')
      .then(m => m.OlvideContrasenaComponent),
  },
  {
    path: 'restablecer/:token',
    title: 'Nueva contraseña · Eduvibe',
    loadComponent: () => import('./paginas/restablecer-contrasena/restablecer-contrasena.component')
      .then(m => m.RestablecerContrasenaComponent),
  },
  {
    path: 'invitacion/:token',
    title: 'Activar cuenta · Eduvibe',
    loadComponent: () => import('./paginas/invitacion/invitacion.component')
      .then(m => m.InvitacionComponent),
  },

  // ------------------------------------------------------------ con sesión
  {
    path: '',
    component: LayoutPrincipalComponent,
    canActivate: [sesionGuard],
    children: [
      { path: '', redirectTo: 'clases', pathMatch: 'full' },

      {
        path: 'clases',
        title: 'Mis clases · Eduvibe',
        loadComponent: () => import('./paginas/clases/lista-clases/lista-clases.component')
          .then(m => m.ListaClasesComponent),
      },
      {
        path: 'clases/nueva',
        canActivate: [adminGuard],
        title: 'Nueva clase · Eduvibe',
        loadComponent: () => import('./paginas/clases/nueva-clase/nueva-clase.component')
          .then(m => m.NuevaClaseComponent),
      },
      {
        path: 'clases/:id/tareas/nueva',
        title: 'Nueva tarea · Eduvibe',
        loadComponent: () => import('./paginas/clases/nueva-tarea/nueva-tarea.component')
          .then(m => m.NuevaTareaComponent),
      },
      {
        path: 'clases/:id/examenes/nuevo',
        title: 'Nuevo examen · Eduvibe',
        loadComponent: () => import('./paginas/clases/nuevo-examen/nuevo-examen.component')
          .then(m => m.NuevoExamenComponent),
      },
      {
        path: 'clases/:id',
        title: 'Clase · Eduvibe',
        loadComponent: () => import('./paginas/clases/detalle-clase/detalle-clase.component')
          .then(m => m.DetalleClaseComponent),
      },
      {
        path: 'tareas/:id',
        title: 'Tarea · Eduvibe',
        loadComponent: () => import('./paginas/tareas/detalle-tarea/detalle-tarea.component')
          .then(m => m.DetalleTareaComponent),
      },
      {
        path: 'examenes/:id',
        title: 'Examen · Eduvibe',
        loadComponent: () => import('./paginas/examenes/detalle-examen/detalle-examen.component')
          .then(m => m.DetalleExamenComponent),
      },
      {
        path: 'examenes/:id/hacer',
        title: 'Haciendo el examen · Eduvibe',
        loadComponent: () => import('./paginas/examenes/hacer-examen/hacer-examen.component')
          .then(m => m.HacerExamenComponent),
      },
      {
        path: 'foro/:id',
        title: 'Hilo · Eduvibe',
        loadComponent: () => import('./paginas/foro/detalle-hilo/detalle-hilo.component')
          .then(m => m.DetalleHiloComponent),
      },
      {
        path: 'calendario',
        title: 'Calendario · Eduvibe',
        loadComponent: () => import('./paginas/calendario/calendario.component')
          .then(m => m.CalendarioComponent),
      },
      {
        path: 'notas',
        canActivate: [alumnoGuard],
        title: 'Notas · Eduvibe',
        loadComponent: () => import('./paginas/notas/notas.component')
          .then(m => m.NotasComponent),
      },
      {
        path: 'correcciones',
        canActivate: [profesorGuard],
        title: 'Calificar · Eduvibe',
        loadComponent: () => import('./paginas/correcciones/correcciones.component')
          .then(m => m.CorreccionesComponent),
      },
      {
        path: 'perfil',
        title: 'Mi perfil · Eduvibe',
        loadComponent: () => import('./paginas/perfil/perfil.component')
          .then(m => m.PerfilComponent),
      },
      {
        path: 'admin/usuarios',
        canActivate: [adminGuard],
        title: 'Usuarios · Eduvibe',
        loadComponent: () => import('./paginas/admin/usuarios/usuarios.component')
          .then(m => m.UsuariosComponent),
      },
      {
        path: 'admin/usuarios/nuevo',
        canActivate: [adminGuard],
        title: 'Nuevo usuario · Eduvibe',
        loadComponent: () => import('./paginas/admin/usuarios/nuevo-usuario/nuevo-usuario.component')
          .then(m => m.NuevoUsuarioComponent),
      },
      {
        path: 'admin/usuarios/:id',
        canActivate: [adminGuard],
        title: 'Usuario · Eduvibe',
        loadComponent: () => import('./paginas/admin/usuarios/detalle-usuario/detalle-usuario.component')
          .then(m => m.DetalleUsuarioComponent),
      },
    ],
  },

  // Cualquier otra cosa vuelve al principio
  { path: '**', redirectTo: '' },
];
