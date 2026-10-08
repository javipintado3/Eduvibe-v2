/**
 * Tipos que devuelve la API.
 *
 * Están en un único fichero porque son declaraciones puras, sin lógica, y
 * repartirlas en diez ficheros de seis líneas solo añadiría importaciones.
 * Se corresponden uno a uno con los DTO del backend.
 */

export type Rol = 'admin' | 'teacher' | 'student' | 'guardian';
export type EstadoCuenta = 'pending' | 'active' | 'disabled';
/** Situación de una solicitud de registro: sin confirmar el correo, esperando al administrador, o ya resuelta. */
export type EstadoSolicitud = 'unverified' | 'pending' | 'approved' | 'rejected';
export type RolEnClase = 'teacher' | 'student';
export type EstadoEntrega = 'draft' | 'submitted' | 'graded';

export interface Usuario {
  id: string;
  email: string;
  name: string;
  role: Rol;
  status: EstadoCuenta;
  avatarUrl: string | null;
  organizationId: string;
  createdAt: string | null;
  totpEnabled: boolean;
}

/** El centro del administrador; allowedDomain es null si las altas no tienen restricción. */
export interface Organizacion {
  id: string;
  name: string;
  allowedDomain: string | null;
}

/** Datos para añadir la cuenta a una app de autenticación. */
export interface ConfiguracionDosPasos {
  secret: string;
  otpauthUri: string;
}

export interface RespuestaAutenticacion {
  token: string;
  expiresAt: string;
  user: Usuario;
}

export interface Invitacion {
  link: string;
  expiresAt: string;
  emailEnviado: boolean;
}

export interface DatosInvitacion {
  name: string;
  email: string;
  organizationName: string;
  expiresAt: string;
}

export interface UsuarioCreado {
  user: Usuario;
  invitation: Invitacion;
}

/** Alguien que ha pedido una cuenta y espera la decisión de la administración. */
export interface SolicitudRegistro {
  id: string;
  name: string;
  email: string;
  /** Texto libre de quien solicita; null si no escribió nada. */
  message: string | null;
  status: EstadoSolicitud;
  verifiedAt: string | null;
  reviewedAt: string | null;
  createdAt: string;
}

export interface Pagina<T> {
  contenido: T[];
  pagina: number;
  tamano: number;
  totalElementos: number;
  totalPaginas: number;
  ultima: boolean;
}

export type ModoVistaClase = 'structured' | 'flexible';

export interface Clase {
  id: string;
  name: string;
  subject: string | null;
  color: string | null;
  imageUrl: string | null;
  miRol: RolEnClase | 'admin' | null;
  proximaEntrega: string | null;
  profesores: string[];
  createdAt: string | null;
  viewMode: ModoVistaClase;
  alumnado: number;
}

/** Una clase en la que participa una persona, para su ficha de administración. */
export interface ClaseDeUsuario {
  classId: string;
  name: string;
  subject: string | null;
  color: string | null;
  roleInClass: RolEnClase;
}

export interface FilaImportada {
  fila: number;
  email: string;
  creado: boolean;
  motivo: string | null;
}

export interface ResultadoImportacion {
  total: number;
  creados: number;
  filas: FilaImportada[];
}

export interface Miembro {
  userId: string;
  name: string;
  email: string;
  roleInClass: RolEnClase;
  enrolledAt: string | null;
}

export interface Tema {
  id: string;
  title: string;
  sortOrder: number;
}

export interface DetalleClase {
  id: string;
  name: string;
  subject: string | null;
  color: string | null;
  imageUrl: string | null;
  miRol: RolEnClase | 'admin' | null;
  puedoEditar: boolean;
  profesores: Miembro[];
  numeroAlumnos: number;
  temas: Tema[];
  viewMode: ModoVistaClase;
}

export interface Tarea {
  id: string;
  classId: string;
  topicId: string | null;
  title: string;
  dueDate: string | null;
  points: number;
  haVencido: boolean;
  miEstado: EstadoEntrega | null;
  miEntregaTarde: boolean | null;
  entregasRecibidas: number | null;
  createdAt: string | null;
}

export interface DetalleTarea {
  id: string;
  classId: string;
  className: string;
  classSubject: string | null;
  topicId: string | null;
  title: string;
  description: string | null;
  dueDate: string | null;
  points: number;
  latePenaltyPercent: number;
  weight: number;
  groupAssignment: boolean;
  haVencido: boolean;
  puedoEditar: boolean;
  miEntrega: Entrega | null;
  /** Su rúbrica, si tiene; null si se califica con una nota suelta. */
  rubric: Rubrica | null;
  /** El subgrupo de quien consulta, en una tarea grupal; null si no está en ninguno todavía. */
  myGroup: Subgrupo | null;
  createdAt: string | null;
}

export interface RubricaCriterio {
  id: string;
  description: string;
  maxPoints: number;
}

export interface Rubrica {
  id: string;
  criteria: RubricaCriterio[];
}

/** Lo puntuado en un criterio concreto, dentro de una calificación ya hecha. */
export interface RubricaPuntuacion {
  criterionId: string;
  description: string;
  maxPoints: number;
  points: number;
}

export interface Calificacion {
  score: number;
  /** La nota tal cual se escribió, antes del descuento por entrega tardía. */
  rawScore: number;
  feedback: string | null;
  gradedByName: string;
  gradedAt: string;
  latePenaltyApplied: boolean;
  /** El desglose por criterio, cuando se calificó con rúbrica; vacío si no. */
  rubricScores: RubricaPuntuacion[];
}

export interface Entrega {
  id: string;
  assignmentId: string;
  assignmentTitle: string;
  studentId: string;
  studentName: string;
  content: string | null;
  fileUrl: string | null;
  status: EstadoEntrega;
  submittedAt: string | null;
  entregadaTarde: boolean;
  grade: Calificacion | null;
  /** Nota rápida del profesorado, independiente de la calificación. */
  teacherNote: string | null;
  /** Sobre cuánto vale la tarea y su peso, para poder calcular la media ponderada. */
  points: number;
  weight: number;
  /** El subgrupo que la entregó, en una tarea grupal; null en el resto. */
  groupName: string | null;
}

/** Una entrega a la espera de corrección, en la cola del profesorado (sin el contenido entregado). */
export interface EntregaPorCorregir {
  submissionId: string;
  assignmentId: string;
  assignmentTitle: string;
  classId: string;
  className: string;
  studentName: string;
  /** El subgrupo que la entregó, en una tarea grupal; entonces `studentName` es solo uno de sus miembros. */
  groupName: string | null;
  submittedAt: string | null;
  entregadaTarde: boolean;
}

export interface Anuncio {
  id: string;
  content: string;
  pinned: boolean;
  authorName: string;
  createdAt: string;
  /** Lo decide el servicio, no el cliente: solo aparece a quien puede borrarlo. */
  puedoBorrar: boolean;
}

export type TipoMaterial = 'pdf' | 'link' | 'video' | 'doc' | 'other';

export interface Material {
  id: string;
  topicId: string | null;
  title: string;
  fileUrl: string | null;
  type: TipoMaterial | null;
  sortOrder: number;
  /** Desde cuándo lo ve el alumnado; null si no tiene restricción. */
  availableFrom: string | null;
  /** Si quien consulta todavía no puede verlo. Al profesorado nunca se le bloquea. */
  bloqueado: boolean;
  createdAt: string | null;
}

export type TipoEventoAgenda = 'exam' | 'holiday' | 'other';
export type OrigenEntradaAgenda = 'event' | 'assignment';

/**
 * Una entrada de la agenda: un evento a mano o una fecha de entrega derivada.
 * `tipo` puede ser un TipoEventoAgenda o 'assignment_due' cuando el origen es
 * una tarea.
 */
export interface EntradaAgenda {
  referencia: string;
  origen: OrigenEntradaAgenda;
  title: string;
  fecha: string;
  tipo: string;
  classId: string | null;
  className: string | null;
  classSubject: string | null;
  classColor: string | null;
}

export interface MiembroSubgrupo {
  id: string;
  name: string;
}

/** Un subgrupo de la clase: un equipo de trabajo reutilizable. */
export interface Subgrupo {
  id: string;
  name: string;
  members: MiembroSubgrupo[];
}

/** Un hilo en la lista del foro de una clase. */
export interface HiloForo {
  id: string;
  topicId: string | null;
  title: string;
  authorName: string;
  postCount: number;
  lastActivityAt: string;
  createdAt: string;
}

export interface MensajeForo {
  id: string;
  content: string;
  authorName: string;
  createdAt: string;
  /** Su autor, o el profesorado de la clase como moderación. */
  puedoBorrar: boolean;
}

export interface DetalleHiloForo {
  id: string;
  classId: string;
  className: string;
  topicId: string | null;
  title: string;
  authorName: string;
  puedoModerar: boolean;
  puedoBorrarHilo: boolean;
  posts: MensajeForo[];
  createdAt: string;
}

export interface Notificacion {
  id: string;
  type: string;
  texto: string;
  payload: Record<string, unknown> | null;
  leida: boolean;
  createdAt: string;
}

/**
 * Resumen de la pantalla de perfil.
 *
 * Un único tipo para los tres roles: cada uno rellena solo los campos que le
 * corresponden, el resto llega a null. Así lo devuelve la API.
 */
export interface ResumenPerfil {
  role: Rol;
  numeroClases: number | null;
  numeroAlumnos: number | null;
  entregasPorCorregir: number | null;
  notaMedia: number | null;
  tareasPendientes: number | null;
  notificacionesNoLeidas: number | null;
  usuariosPorRol: Record<string, number> | null;
  invitacionesPendientes: number | null;
}

export type EstadoIntentoExamen = 'no_empezado' | 'en_curso' | 'entregado';

/** Un examen tal y como aparece en la lista de la clase. */
export interface Examen {
  id: string;
  classId: string;
  topicId: string | null;
  title: string;
  durationMinutes: number;
  dueDate: string | null;
  haVencido: boolean;
  numeroPreguntas: number;
  miEstado: EstadoIntentoExamen | null;
  miNota: number | null;
  intentosRecibidos: number | null;
  createdAt: string | null;
}

/** Pantalla de aterrizaje de un examen: metadatos y, si es alumnado, su propio intento. */
export interface DetalleExamen {
  id: string;
  classId: string;
  className: string;
  classSubject: string | null;
  topicId: string | null;
  title: string;
  description: string | null;
  durationMinutes: number;
  dueDate: string | null;
  haVencido: boolean;
  puedoEditar: boolean;
  numeroPreguntas: number;
  totalPuntos: number;
  miEstado: EstadoIntentoExamen | null;
  miNota: number | null;
  createdAt: string | null;
}

/** Una opción con la respuesta correcta marcada: solo la ve el profesorado. */
export interface OpcionExamen {
  id: string;
  text: string;
  correct: boolean;
}

export interface PreguntaExamen {
  id: string;
  text: string;
  points: number;
  options: OpcionExamen[];
}

/** Una pregunta ya usada en algún examen de la clase, para reutilizarla en uno nuevo. */
export interface PreguntaBanco {
  id: string;
  text: string;
  points: number;
  examTitle: string;
  options: OpcionExamen[];
}

/** Una opción tal y como la ve el alumnado mientras hace el examen: sin marcar cuál es correcta. */
export interface OpcionIntentoExamen {
  id: string;
  text: string;
}

export interface PreguntaIntentoExamen {
  id: string;
  text: string;
  points: number;
  options: OpcionIntentoExamen[];
  /** La opción ya marcada, si se retoma un intento en curso. Null si aún no se ha respondido. */
  miRespuesta: string | null;
}

/** El examen mientras se hace: preguntas sin corrección y el límite de tiempo. */
export interface IntentoExamen {
  attemptId: string;
  examId: string;
  examTitle: string;
  durationMinutes: number;
  startedAt: string;
  deadline: string;
  entregado: boolean;
  questions: PreguntaIntentoExamen[];
}

export interface PreguntaResultadoExamen {
  questionId: string;
  text: string;
  points: number;
  miRespuesta: string | null;
  respuestaCorrecta: string | null;
  acerto: boolean;
}

/** Resultado de un intento recién entregado, con el desglose pregunta a pregunta. */
export interface ResultadoExamen {
  attemptId: string;
  score: number;
  totalPuntos: number;
  submittedAt: string;
  preguntas: PreguntaResultadoExamen[];
}

/** Una fila de la vista de corrección del profesorado: el intento de un alumno. */
export interface IntentoResumenExamen {
  attemptId: string;
  studentId: string;
  studentName: string;
  startedAt: string;
  submittedAt: string | null;
  score: number | null;
  entregado: boolean;
}

/** Un punto de la gráfica de evolución de notas de un alumno: una tarea calificada. */
export interface PuntoNota {
  assignmentTitle: string;
  gradedAt: string;
  percent: number;
}

/** Cuánta gente ha entregado una tarea concreta, para el panel de analítica. */
export interface AnaliticaTarea {
  id: string;
  title: string;
  dueDate: string | null;
  submittedCount: number;
  pendingCount: number;
  completionPercent: number;
}

/** Cómo va un alumno concreto: cuánto ha entregado, su media, y si está en riesgo. */
export interface AnaliticaAlumno {
  userId: string;
  name: string;
  submittedCount: number;
  pendingCount: number;
  totalAssignments: number;
  completionPercent: number;
  /** Media ponderada en % de lo que ya tiene calificado; null si no tiene ninguna nota. */
  averageScore: number | null;
  atRisk: boolean;
  grades: PuntoNota[];
}

/** Panel de analítica de una clase, solo para profesorado y administración. */
export interface AnaliticaClase {
  classSize: number;
  overallCompletionPercent: number;
  atRiskCount: number;
  assignments: AnaliticaTarea[];
  students: AnaliticaAlumno[];
}

/** Forma de los errores que devuelve la API. */
export interface ErrorApi {
  status: number;
  error: string;
  message: string;
  path: string;
  campos?: Record<string, string>;
  timestamp: string;
}
