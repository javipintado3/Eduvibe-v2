/**
 * Contratos de `<app-tabla-datos>`.
 *
 * Misma idea que `ThemedDataGrid` de Fortress: cada pantalla describe QUÉ
 * quiere (columnas y filtros como datos) y la tabla se encarga de CÓMO se
 * pinta. Los filtros no guardan su estado: lo guarda la pantalla (en
 * señales) y la tabla solo avisa de que algo ha cambiado.
 */

export interface ColumnaTabla {
  /** Campo de la fila que se pinta; también es la clave de su plantilla (`appCeldaTabla`) y del orden. */
  campo: string;
  titulo: string;
  /** Ancho CSS opcional ("1px" ajusta la columna a su contenido). */
  ancho?: string;
  alinear?: 'izquierda' | 'derecha';
  /** Muestra en la cabecera un botón para ordenar por esta columna. */
  ordenable?: boolean;
  /** Un clic en esta celda no cuenta como clic en la fila (columna de botones). */
  bloqueaClicFila?: boolean;
}

interface FiltroBase {
  clave: string;
  placeholder?: string;
  /** Fuerza el estado "activo"; si no se indica, un filtro está activo cuando tiene valor. */
  activo?: boolean;
}

export interface FiltroBusqueda extends FiltroBase {
  tipo: 'busqueda';
  valor: string;
  alCambiar: (valor: string) => void;
  /** Milisegundos de espera tras la última tecla antes de avisar. Por defecto 400. */
  retardo?: number;
}

export interface FiltroSeleccion extends FiltroBase {
  tipo: 'seleccion';
  valor: string;
  alCambiar: (valor: string) => void;
  opciones: { valor: string; etiqueta: string }[];
}

export type FiltroTabla = FiltroBusqueda | FiltroSeleccion;

export interface OrdenTabla {
  campo: string;
  direccion: 'asc' | 'desc';
}
