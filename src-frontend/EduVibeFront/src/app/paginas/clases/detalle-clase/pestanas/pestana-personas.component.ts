import { Component, EventEmitter, Input, OnInit, Output, computed, inject, signal } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin } from 'rxjs';

import { ClasesService } from '../../../../core/services/clases.service';
import { ConfirmacionService } from '../../../../core/services/confirmacion.service';
import { SubgruposService } from '../../../../core/services/subgrupos.service';
import { UsuariosService } from '../../../../core/services/usuarios.service';
import { Miembro, Pagina, RolEnClase, Subgrupo, Usuario } from '../../../../core/models';
import { AuthService } from '../../../../core/services/auth.service';
import { paginacionLocal, TAMANO_PAGINA } from '../../../../core/utils/paginacion';
import { AvatarComponent } from '../../../../shared/avatar/avatar.component';
import { AvisoComponent } from '../../../../shared/aviso/aviso.component';
import { CargandoComponent } from '../../../../shared/cargando/cargando.component';
import { DialogoComponent } from '../../../../shared/dialogo/dialogo.component';
import { EstadoVacioComponent } from '../../../../shared/estado-vacio/estado-vacio.component';
import { PaginadorComponent } from '../../../../shared/paginador/paginador.component';
import { PastillaEstadoComponent } from '../../../../shared/pastilla-estado/pastilla-estado.component';
import { CeldaTablaDirective } from '../../../../shared/tabla-datos/celda-tabla.directive';
import { TablaDatosComponent } from '../../../../shared/tabla-datos/tabla-datos.component';
import { ColumnaTabla, FiltroTabla } from '../../../../shared/tabla-datos/tabla-datos.tipos';

/**
 * Pestaña "Personas": quién imparte la clase, quién la cursa, y sus subgrupos.
 *
 * Solo la administración puede dar de baja a alguien, y la propia API impide
 * quitar al último profesor. Aquí el botón se oculta a quien no puede, para no
 * ofrecer una acción que va a terminar en un error.
 *
 * Los subgrupos los gestiona el profesorado de la clase: son la base de las
 * tareas grupales, así que viven aquí, junto al resto de quién es quién.
 */
@Component({
  selector: 'app-pestana-personas',
  standalone: true,
  imports: [
    NgIf, NgFor, ReactiveFormsModule,
    AvatarComponent, CargandoComponent, EstadoVacioComponent, AvisoComponent, DialogoComponent, PastillaEstadoComponent,
    PaginadorComponent, TablaDatosComponent, CeldaTablaDirective,
  ],
  templateUrl: './pestana-personas.component.html',
  styleUrl: './pestana-personas.component.css',
})
export class PestanaPersonasComponent implements OnInit {

  private readonly clasesService = inject(ClasesService);
  private readonly subgruposService = inject(SubgruposService);
  private readonly usuariosService = inject(UsuariosService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly fb = inject(FormBuilder);
  readonly auth = inject(AuthService);

  @Input({ required: true }) claseId!: string;
  @Input() puedoEditar = false;

  /** Se emite al matricular o quitar a alguien, para que la cabecera de la clase se refresque. */
  @Output() cambioMiembros = new EventEmitter<void>();

  readonly miembros = signal<Miembro[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);
  readonly quitando = signal<string | null>(null);

  readonly profesorado = computed(() => this.miembros().filter(m => m.roleInClass === 'teacher'));
  readonly alumnado = computed(() => this.miembros().filter(m => m.roleInClass === 'student'));

  // Diez por página en cada lista, en el cliente: la clase entera se carga de una vez porque
  // los contadores de la cabecera y la matriculación necesitan verla completa.
  readonly paginacionProfesorado = paginacionLocal(() => this.profesorado());
  readonly paginacionAlumnado = paginacionLocal(() => this.alumnado());
  readonly paginacionSubgrupos = paginacionLocal(() => this.subgrupos());

  // --- matriculación ---

  readonly dialogoAnadirAbierto = signal(false);
  readonly rolAAnadir = signal<RolEnClase>('student');
  readonly pagina = signal<Pagina<Usuario> | null>(null);
  readonly seleccionados = signal<Set<string>>(new Set());
  readonly buscando = signal(false);
  readonly anadiendoLote = signal(false);
  readonly errorAnadir = signal<string | null>(null);

  // La búsqueda y la tabla del diálogo, con la misma tabla genérica que el resto de la app
  readonly busqueda = signal('');

  readonly columnasAnadir: ColumnaTabla[] = [
    { campo: 'name', titulo: 'Persona' },
    { campo: 'status', titulo: 'Estado' },
  ];

  readonly filtrosAnadir = computed<FiltroTabla[]>(() => [{
    clave: 'q', tipo: 'busqueda', valor: this.busqueda(),
    placeholder: 'Buscar por nombre o email',
    alCambiar: valor => { this.busqueda.set(valor); this.buscar(0); },
  }]);

  limpiarBusqueda(): void {
    this.busqueda.set('');
    this.buscar(0);
  }

  get tituloAnadir(): string {
    return this.rolAAnadir() === 'teacher' ? 'Añadir profesorado' : 'Añadir alumnado';
  }

  get numSeleccionados(): number {
    return this.seleccionados().size;
  }

  abrirAnadir(rol: RolEnClase): void {
    this.rolAAnadir.set(rol);
    this.busqueda.set('');
    this.pagina.set(null);
    this.seleccionados.set(new Set());
    this.errorAnadir.set(null);
    this.dialogoAnadirAbierto.set(true);
    this.buscar(0);
  }

  /** La tabla propone el nuevo conjunto; aquí solo se guarda. Se conserva al cambiar de página: se añaden todos de golpe. */
  seleccionCambia(nuevos: Set<string>): void {
    this.seleccionados.set(nuevos);
  }

  /**
   * `excludeClassId` deja fuera del todo a quien ya está en la clase —no
   * solo se oculta en el cliente— así que la paginación que devuelve el
   * Pageable del backend (totalElementos, totalPaginas) es siempre exacta.
   */
  buscar(pagina: number): void {
    this.buscando.set(true);
    this.errorAnadir.set(null);

    this.usuariosService.listar({
      role: this.rolAAnadir(), q: this.busqueda(), excludeClassId: this.claseId,
      page: pagina, size: TAMANO_PAGINA,
    }).subscribe({
      next: (resultado) => {
        this.buscando.set(false);
        this.pagina.set(resultado);
      },
      error: (err) => {
        this.buscando.set(false);
        this.errorAnadir.set(AvisoComponent.mensajeDe(err, 'No se ha podido buscar'));
      },
    });
  }

  anadirSeleccionados(): void {
    const ids = Array.from(this.seleccionados());

    if (!ids.length || this.anadiendoLote()) {
      return;
    }

    this.anadiendoLote.set(true);
    this.errorAnadir.set(null);

    forkJoin(ids.map(id => this.clasesService.matricular(this.claseId, id, this.rolAAnadir()))).subscribe({
      next: (miembros) => {
        this.anadiendoLote.set(false);
        this.miembros.update(lista => [...lista, ...miembros]);
        this.seleccionados.set(new Set());
        this.cambioMiembros.emit();
        this.buscar(this.pagina()?.pagina ?? 0);
      },
      error: (err) => {
        this.anadiendoLote.set(false);
        // Algunas altas del lote pueden haberse completado antes del fallo; se recarga
        // para que la lista de miembros y los seleccionables reflejen lo que quedó hecho.
        this.errorAnadir.set(AvisoComponent.mensajeDe(err));
        this.cargar();
        this.buscar(this.pagina()?.pagina ?? 0);
      },
    });
  }

  // --- subgrupos ---
  readonly subgrupos = signal<Subgrupo[]>([]);
  readonly cargandoSubgrupos = signal(true);

  readonly dialogoSubgrupoAbierto = signal(false);
  readonly editando = signal<Subgrupo | null>(null);
  readonly guardandoSubgrupo = signal(false);
  readonly borrandoSubgrupo = signal<string | null>(null);
  readonly errorSubgrupo = signal<string | null>(null);

  readonly formSubgrupo = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    memberIds: this.fb.nonNullable.control<string[]>([]),
  });

  ngOnInit(): void {
    this.cargar();
    this.cargarSubgrupos();
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);

    this.clasesService.miembros(this.claseId).subscribe({
      next: (miembros) => {
        this.miembros.set(miembros);
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(AvisoComponent.mensajeDe(err, 'No se han podido cargar las personas'));
        this.cargando.set(false);
      },
    });
  }

  async quitar(miembro: Miembro): Promise<void> {
    const confirmado = await this.confirmacion.preguntar(`¿Quitar a ${miembro.name} de esta clase?`, {
      titulo: 'Quitar de la clase', textoConfirmar: 'Quitar',
    });
    if (!confirmado) {
      return;
    }

    this.quitando.set(miembro.userId);
    this.error.set(null);

    this.clasesService.desmatricular(this.claseId, miembro.userId).subscribe({
      next: () => {
        this.quitando.set(null);
        this.miembros.update(lista => lista.filter(m => m.userId !== miembro.userId));
        this.cambioMiembros.emit();
      },
      error: (err) => {
        this.quitando.set(null);
        this.error.set(AvisoComponent.mensajeDe(err));
      },
    });
  }

  // ------------------------------------------------------------ subgrupos

  cargarSubgrupos(): void {
    this.cargandoSubgrupos.set(true);

    this.clasesService.subgrupos(this.claseId).subscribe({
      next: (subgrupos) => {
        this.subgrupos.set(subgrupos);
        this.cargandoSubgrupos.set(false);
      },
      error: (err) => {
        this.error.set(AvisoComponent.mensajeDe(err, 'No se han podido cargar los subgrupos'));
        this.cargandoSubgrupos.set(false);
      },
    });
  }

  abrirCrearSubgrupo(): void {
    this.editando.set(null);
    this.formSubgrupo.reset({ name: '', memberIds: [] });
    this.errorSubgrupo.set(null);
    this.dialogoSubgrupoAbierto.set(true);
  }

  abrirEditarSubgrupo(grupo: Subgrupo): void {
    this.editando.set(grupo);
    this.formSubgrupo.reset({ name: grupo.name, memberIds: grupo.members.map(m => m.id) });
    this.errorSubgrupo.set(null);
    this.dialogoSubgrupoAbierto.set(true);
  }

  nombresDe(miembros: { name: string }[]): string {
    return miembros.length ? miembros.map(m => m.name).join(', ') : 'Sin miembros';
  }

  perteneceAlFormulario(userId: string): boolean {
    return this.formSubgrupo.controls.memberIds.value.includes(userId);
  }

  alternarMiembro(userId: string, marcado: boolean): void {
    const actuales = this.formSubgrupo.controls.memberIds.value;
    this.formSubgrupo.controls.memberIds.setValue(
      marcado ? [...actuales, userId] : actuales.filter(id => id !== userId));
  }

  guardarSubgrupo(): void {
    this.formSubgrupo.markAllAsTouched();

    if (this.formSubgrupo.invalid || !this.formSubgrupo.value.memberIds?.length || this.guardandoSubgrupo()) {
      if (!this.formSubgrupo.value.memberIds?.length) {
        this.errorSubgrupo.set('Elige al menos un miembro.');
      }
      return;
    }

    this.guardandoSubgrupo.set(true);
    this.errorSubgrupo.set(null);

    const datos = this.formSubgrupo.getRawValue();
    const edicion = this.editando();

    const peticion = edicion
      ? this.subgruposService.actualizar(edicion.id, datos)
      : this.clasesService.crearSubgrupo(this.claseId, datos);

    peticion.subscribe({
      next: () => {
        this.guardandoSubgrupo.set(false);
        this.dialogoSubgrupoAbierto.set(false);
        this.cargarSubgrupos();
      },
      error: (err) => {
        this.guardandoSubgrupo.set(false);
        this.errorSubgrupo.set(AvisoComponent.mensajeDe(err));
      },
    });
  }

  async borrarSubgrupo(grupo: Subgrupo): Promise<void> {
    const confirmado = await this.confirmacion.preguntar(`¿Borrar el subgrupo "${grupo.name}"?`, {
      titulo: 'Borrar subgrupo', textoConfirmar: 'Borrar',
    });
    if (!confirmado) {
      return;
    }

    this.borrandoSubgrupo.set(grupo.id);
    this.error.set(null);

    this.subgruposService.eliminar(grupo.id).subscribe({
      next: () => {
        this.borrandoSubgrupo.set(null);
        this.subgrupos.update(lista => lista.filter(g => g.id !== grupo.id));
      },
      error: (err) => {
        this.borrandoSubgrupo.set(null);
        this.error.set(AvisoComponent.mensajeDe(err));
      },
    });
  }
}
