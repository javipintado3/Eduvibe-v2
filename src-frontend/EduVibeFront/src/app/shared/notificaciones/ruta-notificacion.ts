import { Notificacion } from '../../core/models';

/**
 * A qué ruta lleva pinchar una notificación, según su tipo y lo que traiga
 * en el payload. Se usa tanto en la campana del navbar como en la lista
 * completa de "Mi perfil", para no mantener la misma regla en dos sitios.
 */
export function rutaDeNotificacion(notificacion: Notificacion): string | null {
  const payload = notificacion.payload ?? {};

  if (notificacion.type === 'announcement_created' && payload['classId']) {
    return `/clases/${payload['classId']}`;
  }
  if ((notificacion.type === 'assignment_created' || notificacion.type === 'grade_published') && payload['assignmentId']) {
    return `/tareas/${payload['assignmentId']}`;
  }
  if (notificacion.type === 'exam_created' && payload['examId']) {
    return `/examenes/${payload['examId']}`;
  }
  if (notificacion.type === 'registration_requested') {
    return '/admin/solicitudes';
  }
  return null;
}
