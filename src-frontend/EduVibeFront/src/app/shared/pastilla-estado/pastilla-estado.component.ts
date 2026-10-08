import { Component, Input, computed, signal } from '@angular/core';

import { EstadoCuenta, EstadoSolicitud, EstadoEntrega, EstadoIntentoExamen, Rol, TipoEventoAgenda, TipoMaterial } from '../../core/models';

type Clave = EstadoCuenta | EstadoSolicitud | EstadoEntrega | EstadoIntentoExamen | Rol | 'sin-empezar' | 'tarde' | TipoMaterial
  | TipoEventoAgenda | 'assignment_due';

interface Aspecto {
  texto: string;
  clase: string;
}

/**
 * Traduce los valores que devuelve la API a una etiqueta en castellano con su
 * color.
 *
 * Está centralizado aquí y no repartido por las plantillas para que "student"
 * se lea igual en todas las pantallas: si cada vista lo tradujese por su
 * cuenta, acabarían conviviendo "alumno", "Alumno" y "estudiante".
 */
@Component({
  selector: 'app-pastilla-estado',
  standalone: true,
  template: `<span class="pastilla" [class]="'pastilla ' + aspecto().clase">{{ aspecto().texto }}</span>`,
})
export class PastillaEstadoComponent {

  private static readonly ASPECTOS: Record<Clave, Aspecto> = {
    // Cuentas
    pending:  { texto: 'Pendiente',    clase: 'pastilla-ambar' },
    active:   { texto: 'Activa',       clase: 'pastilla-verde' },
    disabled: { texto: 'Desactivada',  clase: 'pastilla-gris' },

    // Solicitudes de registro (pending es el mismo de las cuentas)
    unverified: { texto: 'Sin confirmar', clase: 'pastilla-gris' },
    approved:   { texto: 'Aceptada',      clase: 'pastilla-verde' },
    rejected:   { texto: 'Rechazada',     clase: 'pastilla-roja' },

    // Roles
    admin:    { texto: 'Administración', clase: 'pastilla-azul' },
    teacher:  { texto: 'Profesorado',    clase: 'pastilla-verde' },
    student:  { texto: 'Alumnado',       clase: 'pastilla-gris' },
    guardian: { texto: 'Tutor legal',    clase: 'pastilla-gris' },

    // Entregas
    draft:        { texto: 'Borrador',    clase: 'pastilla-gris' },
    submitted:    { texto: 'Entregada',   clase: 'pastilla-azul' },
    graded:       { texto: 'Calificada',  clase: 'pastilla-verde' },
    'sin-empezar':{ texto: 'Sin empezar', clase: 'pastilla-gris' },
    tarde:        { texto: 'Fuera de plazo', clase: 'pastilla-roja' },

    // Intentos de examen
    no_empezado: { texto: 'Sin empezar', clase: 'pastilla-gris' },
    en_curso:    { texto: 'En curso',    clase: 'pastilla-ambar' },
    entregado:   { texto: 'Entregado',   clase: 'pastilla-verde' },

    // Materiales
    pdf:   { texto: 'PDF',    clase: 'pastilla-roja' },
    link:  { texto: 'Enlace', clase: 'pastilla-azul' },
    video: { texto: 'Vídeo',  clase: 'pastilla-ambar' },
    doc:   { texto: 'Documento', clase: 'pastilla-azul' },
    other: { texto: 'Otro',   clase: 'pastilla-gris' },

    // Agenda
    exam:            { texto: 'Examen',        clase: 'pastilla-roja' },
    holiday:         { texto: 'Festivo',       clase: 'pastilla-verde' },
    assignment_due:  { texto: 'Entrega',       clase: 'pastilla-azul' },
  };

  private readonly _valor = signal<Clave | null>(null);

  /**
   * Acepta cualquier string, no solo `Clave`: algunas fuentes (la agenda,
   * que mezcla eventos y tareas) devuelven su tipo como texto sin tipar en
   * el backend. Un valor que no está en ASPECTOS cae al guion por defecto en
   * lugar de romper la plantilla.
   */
  @Input({ required: true })
  set valor(v: string | null | undefined) {
    this._valor.set((v as Clave) ?? null);
  }

  readonly aspecto = computed<Aspecto>(() => {
    const clave = this._valor();
    return (clave && PastillaEstadoComponent.ASPECTOS[clave])
      || { texto: '—', clase: 'pastilla-gris' };
  });
}
