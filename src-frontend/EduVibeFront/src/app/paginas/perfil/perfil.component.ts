import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe, NgFor, NgIf } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { forkJoin, map, switchMap } from 'rxjs';

import { AuthService } from '../../core/services/auth.service';
import { ClasesService } from '../../core/services/clases.service';
import { NotificacionesService } from '../../core/services/notificaciones.service';
import { PerfilService } from '../../core/services/perfil.service';
import { SubidasService } from '../../core/services/subidas.service';
import { Clase, Notificacion, Pagina, ResumenPerfil } from '../../core/models';
import { AvatarComponent } from '../../shared/avatar/avatar.component';
import { AvisoComponent } from '../../shared/aviso/aviso.component';
import { CargandoComponent } from '../../shared/cargando/cargando.component';
import { EstadoVacioComponent } from '../../shared/estado-vacio/estado-vacio.component';
import { PastillaEstadoComponent } from '../../shared/pastilla-estado/pastilla-estado.component';
import { TarjetaClaseComponent } from '../../shared/tarjeta-clase/tarjeta-clase.component';
import { CeldaTablaDirective } from '../../shared/tabla-datos/celda-tabla.directive';
import { TablaDatosComponent } from '../../shared/tabla-datos/tabla-datos.component';
import { ColumnaTabla, FiltroTabla } from '../../shared/tabla-datos/tabla-datos.tipos';
import { rutaDeNotificacion } from '../../shared/notificaciones/ruta-notificacion';
import { FechaPipe } from '../../shared/pipes/fecha.pipe';

/** Cómo se lee cada rol en el desglose de administración. */
const ETIQUETAS_ROL: Record<string, string> = {
  admin: 'Administración', teacher: 'Profesorado', student: 'Alumnado', guardian: 'Tutores legales',
};

/**
 * Perfil: quién eres, y lo que te interesa ver según tu rol.
 *
 * No es la misma pantalla para todos —eso es justo el punto—: al alumnado le
 * importa lo que tiene pendiente, al profesorado cuánto le queda por
 * corregir, y a administración el estado del centro. `ResumenPerfil` llega
 * ya calculado del backend; aquí solo se decide qué tarjetas enseñar.
 */
@Component({
  selector: 'app-perfil',
  standalone: true,
  imports: [
    NgIf, NgFor, RouterLink, DecimalPipe,
    AvatarComponent, AvisoComponent, CargandoComponent, EstadoVacioComponent,
    PastillaEstadoComponent, TarjetaClaseComponent, FechaPipe,
    TablaDatosComponent, CeldaTablaDirective,
  ],
  templateUrl: './perfil.component.html',
  styleUrl: './perfil.component.css',
})
export class PerfilComponent implements OnInit {

  private readonly clasesService = inject(ClasesService);
  private readonly perfilService = inject(PerfilService);
  private readonly subidasService = inject(SubidasService);
  private readonly notificacionesService = inject(NotificacionesService);
  private readonly router = inject(Router);
  readonly auth = inject(AuthService);

  readonly resumen = signal<ResumenPerfil | null>(null);
  readonly clases = signal<Clase[]>([]);
  readonly notificaciones = signal<Notificacion[]>([]);
  readonly paginaNotificaciones = signal<Pagina<Notificacion> | null>(null);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly marcandoTodas = signal(false);
  readonly guardandoAvatar = signal(false);

  readonly usuariosPorRol = computed(() => {
    const usuarios = this.resumen()?.usuariosPorRol;
    if (!usuarios) {
      return [];
    }
    const orden = ['admin', 'teacher', 'student', 'guardian'];
    return Object.entries(usuarios).sort((a, b) => orden.indexOf(a[0]) - orden.indexOf(b[0]));
  });

  readonly hayNoLeidas = computed(() => this.notificaciones().some(n => !n.leida));

  ngOnInit(): void {
    this.cargando.set(true);
    this.error.set(null);

    forkJoin({
      resumen: this.perfilService.resumen(),
      // Solo se enseñan las primeras clases como acceso rápido: basta la primera página
      clases: this.clasesService.misClases().pipe(map(pagina => pagina.contenido)),
      notificaciones: this.notificacionesService.misNotificaciones(),
    }).subscribe({
      next: ({ resumen, clases, notificaciones }) => {
        this.resumen.set(resumen);
        this.clases.set(clases);
        this.notificaciones.set(notificaciones.contenido);
        this.paginaNotificaciones.set(notificaciones);
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(AvisoComponent.mensajeDe(err, 'No se ha podido cargar el perfil'));
        this.cargando.set(false);
      },
    });
  }

  readonly soloNoLeidas = signal(false);

  readonly columnasNotificaciones: ColumnaTabla[] = [
    { campo: 'texto', titulo: 'Notificación' },
    { campo: 'createdAt', titulo: 'Recibida', ancho: '1px' },
  ];

  readonly filtrosNotificaciones = computed<FiltroTabla[]>(() => [{
    clave: 'estado', tipo: 'seleccion', valor: this.soloNoLeidas() ? 'no-leidas' : '',
    placeholder: 'Todas',
    opciones: [{ valor: 'no-leidas', etiqueta: 'Sin leer' }],
    alCambiar: valor => { this.soloNoLeidas.set(valor === 'no-leidas'); this.cargarNotificaciones(0); },
  }]);

  limpiarFiltroNotificaciones(): void {
    this.soloNoLeidas.set(false);
    this.cargarNotificaciones(0);
  }

  /** Cambia de página o de filtro; las notificaciones las pagina y filtra el servidor, de diez en diez. */
  cargarNotificaciones(pagina: number): void {
    this.notificacionesService.misNotificaciones(pagina, this.soloNoLeidas()).subscribe(resultado => {
      this.notificaciones.set(resultado.contenido);
      this.paginaNotificaciones.set(resultado);
    });
  }

  etiquetaRol(rol: string): string {
    return ETIQUETAS_ROL[rol] ?? rol;
  }

  /**
   * Cambia la foto pinchando directamente en el círculo del avatar, sin pasar
   * por una zona de subida aparte: se elige el archivo, se sube, y en cuanto
   * hay URL se guarda en el perfil, igual que el acceso rápido de la portada
   * de una clase.
   */
  cambiarFotoDirecta(evento: Event): void {
    const input = evento.target as HTMLInputElement;
    const archivo = input.files?.[0];
    if (!archivo || this.guardandoAvatar()) {
      return;
    }

    this.guardandoAvatar.set(true);
    this.error.set(null);

    this.subidasService.subir(archivo, 'imagen').pipe(
      switchMap(url => this.perfilService.actualizarAvatar(url)),
    ).subscribe({
      next: () => {
        this.guardandoAvatar.set(false);
        this.auth.refrescarUsuario().subscribe();
      },
      error: (err) => {
        this.guardandoAvatar.set(false);
        this.error.set(AvisoComponent.mensajeDe(err, 'No se ha podido actualizar la foto'));
      },
    });
    input.value = '';
  }

  marcarLeida(notificacion: Notificacion): void {
    if (notificacion.leida) {
      return;
    }
    this.notificacionesService.marcarLeida(notificacion.id).subscribe(() => {
      this.notificaciones.update(lista =>
        lista.map(n => n.id === notificacion.id ? { ...n, leida: true } : n));
    });
  }

  /** Marca leída y, si la notificación trae dónde ir, navega. */
  irA(notificacion: Notificacion): void {
    this.marcarLeida(notificacion);
    const ruta = rutaDeNotificacion(notificacion);
    if (ruta) {
      this.router.navigateByUrl(ruta);
    }
  }

  marcarTodasLeidas(): void {
    if (this.marcandoTodas() || !this.hayNoLeidas()) {
      return;
    }
    this.marcandoTodas.set(true);

    this.notificacionesService.marcarTodasLeidas().subscribe({
      next: () => {
        this.marcandoTodas.set(false);
        // Recarga en vez de marcar a mano: con el filtro "sin leer" puesto, la lista debe quedar vacía
        this.cargarNotificaciones(0);
      },
      error: () => this.marcandoTodas.set(false),
    });
  }
}
