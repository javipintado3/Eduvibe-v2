import { computed, signal } from '@angular/core';

import { FiltroTabla, OrdenTabla } from '../../shared/tabla-datos/tabla-datos.tipos';
import { TAMANO_PAGINA, totalDePaginas } from './paginacion';

/** Una opción de un filtro desplegable; `valor` vacío es "todos". */
export interface OpcionFiltro {
  valor: string;
  etiqueta: string;
}

export interface OpcionesListaLocal<T> {
  /** Textos de la fila entre los que busca el buscador (sin distinguir mayúsculas ni tildes). */
  textos?: (fila: T) => (string | null | undefined)[];
  /** Un filtro desplegable por clave: dice si la fila encaja con el valor elegido. */
  selectores?: Record<string, {
    placeholder: string;
    /** Si devuelve false el filtro no se ofrece (p. ej. cuando solo hay una unidad). */
    visible?: () => boolean;
    opciones: () => OpcionFiltro[];
    encaja: (fila: T, valor: string) => boolean;
  }>;
  /** Cómo se ordena cada campo de la tabla; los que no estén aquí no se pueden ordenar. */
  comparadores?: Record<string, (a: T, b: T) => number>;
  placeholderBusqueda?: string;
}

const sinTildes = (texto: string) => texto.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase();

/** Compara dos valores opcionales dejando los vacíos siempre al final del orden ascendente. */
export function comparar(a: string | number | null | undefined, b: string | number | null | undefined): number {
  if (a == null || b == null) {
    return a == null ? (b == null ? 0 : 1) : -1;
  }
  return typeof a === 'number' && typeof b === 'number'
    ? a - b
    : String(a).localeCompare(String(b), 'es', { numeric: true, sensitivity: 'base' });
}

/**
 * Búsqueda, filtros, orden y paginación en el cliente para `<app-tabla-datos>`.
 *
 * Es la contrapartida de lo que el servidor hace en las tablas paginadas por
 * API: las listas que ya llegan enteras (porque otra cosa necesita el conjunto
 * completo, como una media o un contador) se filtran y ordenan aquí con la
 * misma forma de usarlas, sin que cada pantalla repita las mismas señales.
 *
 * Cualquier cambio de búsqueda, filtro u orden vuelve a la primera página, y
 * la página pedida se recorta sola si el resultado se acorta.
 */
export function listaLocal<T>(origen: () => readonly T[], opciones: OpcionesListaLocal<T> = {}) {
  const busqueda = signal('');
  const selecciones = signal<Record<string, string>>({});
  const orden = signal<OrdenTabla | null>(null);
  const solicitada = signal(0);

  const filtradas = computed(() => {
    const consulta = sinTildes(busqueda().trim());
    const elegidos = Object.entries(selecciones()).filter(([, valor]) => valor);

    let filas = origen().filter(fila =>
      (!consulta || (opciones.textos?.(fila) ?? []).some(texto => sinTildes(texto ?? '').includes(consulta)))
      && elegidos.every(([clave, valor]) => opciones.selectores?.[clave]?.encaja(fila, valor) ?? true));

    const actual = orden();
    const comparador = actual ? opciones.comparadores?.[actual.campo] : undefined;
    if (actual && comparador) {
      const signo = actual.direccion === 'asc' ? 1 : -1;
      filas = [...filas].sort((a, b) => signo * comparador(a, b));
    }
    return filas;
  });

  const total = computed(() => filtradas().length);
  const totalPaginas = computed(() => totalDePaginas(total()));
  const pagina = computed(() => Math.min(solicitada(), totalPaginas() - 1));
  const visibles = computed(() => filtradas().slice(pagina() * TAMANO_PAGINA, (pagina() + 1) * TAMANO_PAGINA));

  const volverAlPrincipio = () => solicitada.set(0);

  const filtros = computed<FiltroTabla[]>(() => [
    ...(opciones.textos ? [{
      clave: 'q', tipo: 'busqueda' as const, valor: busqueda(),
      placeholder: opciones.placeholderBusqueda ?? 'Buscar',
      alCambiar: (valor: string) => { busqueda.set(valor); volverAlPrincipio(); },
    }] : []),
    ...Object.entries(opciones.selectores ?? {}).filter(([, selector]) => selector.visible?.() ?? true).map(([clave, selector]) => ({
      clave, tipo: 'seleccion' as const, valor: selecciones()[clave] ?? '',
      placeholder: selector.placeholder, opciones: selector.opciones(),
      alCambiar: (valor: string) => { selecciones.update(actual => ({ ...actual, [clave]: valor })); volverAlPrincipio(); },
    })),
  ]);

  return {
    filtros,
    orden,
    pagina,
    total,
    totalPaginas,
    visibles,
    /** Cuántas filas había antes de filtrar, para distinguir "no hay nada" de "nada coincide". */
    sinFiltrar: computed(() => origen().length),
    irA: (destino: number) => solicitada.set(destino),
    ordenar: (nuevo: OrdenTabla | null) => { orden.set(nuevo); volverAlPrincipio(); },
    limpiarFiltros: () => { busqueda.set(''); selecciones.set({}); volverAlPrincipio(); },
    reiniciar: volverAlPrincipio,
  };
}

export type ListaLocal<T> = ReturnType<typeof listaLocal<T>>;
