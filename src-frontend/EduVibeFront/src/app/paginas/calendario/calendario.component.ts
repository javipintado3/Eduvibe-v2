import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { forkJoin } from 'rxjs';

import { AuthService } from '../../core/services/auth.service';
import { CalendarioService } from '../../core/services/calendario.service';
import { ClasesService } from '../../core/services/clases.service';
import { ConfirmacionService } from '../../core/services/confirmacion.service';
import { Clase, EntradaAgenda, TipoEventoAgenda } from '../../core/models';
import { AvisoComponent } from '../../shared/aviso/aviso.component';
import { CargandoComponent } from '../../shared/cargando/cargando.component';
import { DialogoComponent } from '../../shared/dialogo/dialogo.component';
import { EstadoVacioComponent } from '../../shared/estado-vacio/estado-vacio.component';
import { PastillaEstadoComponent } from '../../shared/pastilla-estado/pastilla-estado.component';
import { comparar, listaLocal } from '../../core/utils/lista-local';
import { CeldaTablaDirective } from '../../shared/tabla-datos/celda-tabla.directive';
import { TablaDatosComponent } from '../../shared/tabla-datos/tabla-datos.component';
import { ColumnaTabla } from '../../shared/tabla-datos/tabla-datos.tipos';

const MESES = [
  'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
  'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre',
];

const DIAS_SEMANA = ['L', 'M', 'X', 'J', 'V', 'S', 'D'];

/** Color neutro para un evento de todo el centro, que no tiene classColor. */
const COLOR_SIN_CLASE = '#64748b';

/**
 * Máximo de entradas que se listan dentro de una celda antes de resumir en "+N".
 *
 * Son dos porque la celda tiene una altura fija (ver el CSS): con más, o el
 * contenido se sale, o la fila se alarga y la rejilla queda desigual. El día
 * completo, con todo lo que tiene, se ve al pincharlo, en el panel de debajo.
 */
const MAX_POR_CELDA = 2;

interface Celda {
  fecha: Date;
  /** Clave YYYY-MM-DD en horario local: con qué se agrupan y se comparan los días. */
  clave: string;
  enMes: boolean;
  hoy: boolean;
  entradas: EntradaAgenda[];
}

/**
 * Agenda del centro, como un calendario de verdad: rejilla del mes con un
 * punto de color por entrada en cada día, y el detalle del día elegido
 * debajo.
 *
 * La API ya devuelve eventos a mano y fechas de entrega mezclados y
 * ordenados (ver CalendarService en el backend); aquí solo hace falta
 * agruparlos por día para pintar la rejilla.
 */
@Component({
  selector: 'app-calendario',
  standalone: true,
  imports: [
    NgIf, NgFor, ReactiveFormsModule,
    CargandoComponent, EstadoVacioComponent, DialogoComponent, AvisoComponent, PastillaEstadoComponent,
    TablaDatosComponent, CeldaTablaDirective,
  ],
  templateUrl: './calendario.component.html',
  styleUrl: './calendario.component.css',
})
export class CalendarioComponent implements OnInit {

  private readonly calendarioService = inject(CalendarioService);
  private readonly clasesService = inject(ClasesService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  readonly auth = inject(AuthService);

  readonly diasSemana = DIAS_SEMANA;

  readonly mesActual = signal(this.inicioDeMes(new Date()));
  readonly nombreMes = computed(() => {
    const mes = this.mesActual();
    return `${MESES[mes.getMonth()]} ${mes.getFullYear()}`;
  });

  readonly entradas = signal<EntradaAgenda[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<string | null>(null);

  // --- selección en lote, para borrar varios eventos de golpe ---
  readonly seleccionados = signal<Set<string>>(new Set());
  readonly aplicandoLote = signal(false);

  /** Siempre hay un día elegido: así el panel de detalle y el alta tienen dónde apoyarse. */
  readonly diaSeleccionado = signal(this.claveDe(new Date()));

  readonly celdas = computed<Celda[]>(() => this.construirCeldas(this.mesActual(), this.entradas()));

  readonly entradasDelDia = computed<EntradaAgenda[]>(() => {
    const clave = this.diaSeleccionado();
    return this.entradas().filter(e => this.claveDe(new Date(e.fecha)) === clave);
  });

  /** Búsqueda, filtro por tipo, orden y diez por página en el panel del día, para que un día cargado no alargue la pantalla. */
  readonly listaDia = listaLocal(() => this.entradasDelDia(), {
    placeholderBusqueda: 'Buscar en el día',
    textos: entrada => [entrada.title, entrada.className, entrada.classSubject],
    selectores: {
      tipo: {
        placeholder: 'Todos los tipos',
        opciones: () => [
          { valor: 'exam', etiqueta: 'Examen' },
          { valor: 'holiday', etiqueta: 'Festivo' },
          { valor: 'other', etiqueta: 'Otro' },
          { valor: 'assignment_due', etiqueta: 'Entrega' },
        ],
        encaja: (entrada, valor) => entrada.tipo === valor,
      },
    },
    comparadores: {
      fecha: (a, b) => comparar(a.fecha, b.fecha),
      title: (a, b) => comparar(a.title, b.title),
      tipo: (a, b) => comparar(a.tipo, b.tipo),
      className: (a, b) => comparar(a.className, b.className),
    },
  });

  readonly columnasDia: ColumnaTabla[] = [
    { campo: 'fecha', titulo: 'Hora', ancho: '1px', ordenable: true },
    { campo: 'title', titulo: 'Entrada', ordenable: true },
    { campo: 'tipo', titulo: 'Tipo', ordenable: true },
    { campo: 'className', titulo: 'Clase', ordenable: true },
  ];

  readonly idEntrada = (entrada: EntradaAgenda) => entrada.referencia;

  readonly tituloDia = computed(() => {
    const [anio, mes, dia] = this.diaSeleccionado().split('-').map(Number);
    const fecha = new Date(anio, mes - 1, dia);
    const diaSemana = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'][fecha.getDay()];
    // Mayúscula solo en la primera letra: text-transform:capitalize la pondría en cada palabra
    const frase = `${diaSemana}, ${dia} de ${MESES[mes - 1]}`;
    return frase.charAt(0).toUpperCase() + frase.slice(1);
  });

  /** Las clases que gestiono: para el selector del formulario y para saber qué eventos puedo borrar. */
  readonly misClases = signal<Clase[]>([]);
  private readonly idsDeMisClases = computed(() => new Set(this.misClases().map(c => c.id)));

  readonly puedeCrear = computed(() => this.auth.esAdmin() || this.auth.esProfesor());

  readonly dialogoAbierto = signal(false);
  readonly creando = signal(false);
  readonly errorFormulario = signal<string | null>(null);

  readonly formulario = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(200)]],
    eventDate: ['', [Validators.required]],
    type: this.fb.nonNullable.control<TipoEventoAgenda | ''>(''),
    // Sin Validators.required: vacío es válido para la administración (evento
    // de todo el centro); para el resto se comprueba a mano en crear().
    classId: [''],
  });

  ngOnInit(): void {
    if (this.puedeCrear()) {
      this.clasesService.todasMisClases().subscribe(clases => this.misClases.set(clases));
    }
    this.cargar();
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set(null);

    const desde = this.mesActual();
    const hasta = new Date(desde.getFullYear(), desde.getMonth() + 1, 1);

    this.calendarioService.agenda(desde, hasta).subscribe({
      next: (entradas) => {
        this.entradas.set(entradas);
        this.cargando.set(false);
      },
      error: (err) => {
        this.error.set(AvisoComponent.mensajeDe(err, 'No se ha podido cargar la agenda'));
        this.cargando.set(false);
      },
    });
  }

  mesAnterior(): void {
    this.cambiarMes(-1);
  }

  mesSiguiente(): void {
    this.cambiarMes(1);
  }

  hoy(): void {
    this.mesActual.set(this.inicioDeMes(new Date()));
    this.diaSeleccionado.set(this.claveDe(new Date()));
    this.listaDia.reiniciar();
    this.limpiarSeleccion();
    this.cargar();
  }

  seleccionar(celda: Celda): void {
    this.diaSeleccionado.set(celda.clave);
    this.listaDia.reiniciar();
    this.limpiarSeleccion();
  }

  /** Solo se puede borrar un evento a mano de una clase que se gestiona; las entregas no son eventos reales. */
  puedeBorrar(entrada: EntradaAgenda): boolean {
    if (entrada.origen !== 'event') {
      return false;
    }
    if (this.auth.esAdmin()) {
      return true;
    }
    return entrada.classId !== null && this.idsDeMisClases().has(entrada.classId);
  }

  colorDe(entrada: EntradaAgenda): string {
    return entrada.classColor ?? COLOR_SIN_CLASE;
  }

  /**
   * A dónde lleva pinchar en la entrada: la tarea si es una fecha de
   * entrega, la clase si es un evento de una clase concreta. Los eventos de
   * todo el centro (sin classId) no tienen a dónde ir, así que no son
   * clicables.
   */
  destinoDe(entrada: EntradaAgenda): unknown[] | null {
    if (entrada.origen === 'assignment') {
      return ['/tareas', entrada.referencia];
    }
    return entrada.classId ? ['/clases', entrada.classId] : null;
  }

  /** Pinchar una fila lleva a su tarea o clase; los eventos de todo el centro no tienen a dónde ir. */
  abrirEntrada(entrada: EntradaAgenda): void {
    const destino = this.destinoDe(entrada);
    if (destino) {
      this.router.navigate(destino);
    }
  }

  abrirDialogo(): void {
    this.formulario.reset({
      title: '',
      eventDate: `${this.diaSeleccionado()}T09:00`,
      type: '',
      classId: this.auth.esAdmin() ? '' : (this.misClases()[0]?.id ?? ''),
    });
    this.errorFormulario.set(null);
    this.dialogoAbierto.set(true);
  }

  crear(): void {
    this.formulario.markAllAsTouched();

    // Solo la administración puede dejar la clase sin elegir (evento de todo el centro)
    if (!this.auth.esAdmin() && !this.formulario.controls.classId.value) {
      return;
    }
    if (this.formulario.invalid || this.creando()) {
      return;
    }

    this.creando.set(true);
    this.errorFormulario.set(null);

    const { title, eventDate, type, classId } = this.formulario.getRawValue();

    this.calendarioService.crear({
      title,
      // El input datetime-local da "2026-10-02T18:30"; la API espera un
      // instante en UTC, y eso es justo lo que hace toISOString()
      eventDate: new Date(eventDate).toISOString(),
      // El @Pattern del backend admite null pero no "": sin esto, dejar el
      // tipo sin especificar devolvía 400 en vez de crear el evento.
      type: type || undefined,
      classId: classId || null,
    }).subscribe({
      next: () => {
        this.creando.set(false);
        this.dialogoAbierto.set(false);
        this.cargar();
      },
      error: (err) => {
        this.creando.set(false);
        this.errorFormulario.set(AvisoComponent.mensajeDe(err));
      },
    });
  }

  // --- selección en lote ---

  /**
   * La tabla propone el nuevo conjunto marcado. Las entregas no son eventos
   * reales (ver puedeBorrar), así que se descartan: "seleccionar todo" solo
   * marca lo que se puede borrar.
   */
  seleccionCambia(nuevos: Set<string>): void {
    const borrables = new Set(this.entradasDelDia().filter(e => this.puedeBorrar(e)).map(e => e.referencia));
    this.seleccionados.set(new Set([...nuevos].filter(id => borrables.has(id))));
  }

  limpiarSeleccion(): void {
    this.seleccionados.set(new Set());
  }

  /**
   * Borra los eventos seleccionados de golpe. Las entregas nunca están
   * seleccionables (no son eventos reales, ver puedeBorrar), así que el
   * lote siempre son eventos de agenda de verdad.
   */
  async eliminarSeleccionadas(): Promise<void> {
    const ids = Array.from(this.seleccionados());
    if (!ids.length || this.aplicandoLote()) {
      return;
    }

    const confirmado = await this.confirmacion.preguntar(
      `¿Borrar ${ids.length} evento(s) de la agenda?`,
      { titulo: 'Borrar eventos', textoConfirmar: 'Borrar' });
    if (!confirmado) {
      return;
    }

    this.aplicandoLote.set(true);
    this.error.set(null);

    forkJoin(ids.map(id => this.calendarioService.eliminar(id))).subscribe({
      next: () => {
        this.aplicandoLote.set(false);
        this.entradas.update(lista => lista.filter(e => !ids.includes(e.referencia)));
        this.limpiarSeleccion();
      },
      error: (err) => {
        this.aplicandoLote.set(false);
        this.error.set(AvisoComponent.mensajeDe(err));
        this.limpiarSeleccion();
        this.cargar();
      },
    });
  }

  horaDe(fechaIso: string): string {
    const fecha = new Date(fechaIso);
    const hora = String(fecha.getHours()).padStart(2, '0');
    const minuto = String(fecha.getMinutes()).padStart(2, '0');
    return `${hora}:${minuto}`;
  }

  entradasVisiblesDe(celda: Celda): EntradaAgenda[] {
    return celda.entradas.slice(0, MAX_POR_CELDA);
  }

  masOcultosDe(celda: Celda): number {
    return Math.max(0, celda.entradas.length - MAX_POR_CELDA);
  }

  private cambiarMes(delta: number): void {
    const mes = this.mesActual();
    const nuevoMes = new Date(mes.getFullYear(), mes.getMonth() + delta, 1);
    this.mesActual.set(nuevoMes);
    this.diaSeleccionado.set(this.claveDe(nuevoMes));
    this.listaDia.reiniciar();
    this.limpiarSeleccion();
    this.cargar();
  }

  /**
   * La rejilla siempre empieza en lunes y termina en domingo, aunque eso
   * signifique enseñar unos días del mes anterior y del siguiente: así las
   * semanas quedan completas y la rejilla no cambia de alto de un mes a otro.
   */
  private construirCeldas(mes: Date, entradas: EntradaAgenda[]): Celda[] {
    const primerDiaMes = new Date(mes.getFullYear(), mes.getMonth(), 1);
    const ultimoDiaMes = new Date(mes.getFullYear(), mes.getMonth() + 1, 0);

    const offsetInicio = (primerDiaMes.getDay() + 6) % 7; // lunes = 0
    const inicio = new Date(primerDiaMes);
    inicio.setDate(inicio.getDate() - offsetInicio);

    const offsetFin = 6 - ((ultimoDiaMes.getDay() + 6) % 7);
    const fin = new Date(ultimoDiaMes);
    fin.setDate(fin.getDate() + offsetFin);

    const porDia = new Map<string, EntradaAgenda[]>();
    for (const entrada of entradas) {
      const clave = this.claveDe(new Date(entrada.fecha));
      const lista = porDia.get(clave);
      lista ? lista.push(entrada) : porDia.set(clave, [entrada]);
    }

    const hoy = this.claveDe(new Date());
    const celdas: Celda[] = [];
    const cursor = new Date(inicio);
    while (cursor <= fin) {
      const clave = this.claveDe(cursor);
      celdas.push({
        fecha: new Date(cursor),
        clave,
        enMes: cursor.getMonth() === mes.getMonth(),
        hoy: clave === hoy,
        entradas: porDia.get(clave) ?? [],
      });
      cursor.setDate(cursor.getDate() + 1);
    }
    return celdas;
  }

  private claveDe(fecha: Date): string {
    const anio = fecha.getFullYear();
    const mes = String(fecha.getMonth() + 1).padStart(2, '0');
    const dia = String(fecha.getDate()).padStart(2, '0');
    return `${anio}-${mes}-${dia}`;
  }

  private inicioDeMes(fecha: Date): Date {
    return new Date(fecha.getFullYear(), fecha.getMonth(), 1);
  }
}
