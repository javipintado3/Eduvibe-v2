import { signal } from '@angular/core';

import { comparar, listaLocal } from './lista-local';

interface Fila { nombre: string; estado: string; nota: number | null; }

const filas = (): Fila[] => [
  { nombre: 'Álvaro', estado: 'a', nota: 7 },
  { nombre: 'Berta', estado: 'b', nota: null },
  { nombre: 'Carlos', estado: 'a', nota: 3 },
];

function crear(origen = signal(filas())) {
  return listaLocal(() => origen(), {
    textos: fila => [fila.nombre],
    selectores: {
      estado: {
        placeholder: 'Estado',
        opciones: () => [{ valor: 'a', etiqueta: 'A' }],
        encaja: (fila, valor) => fila.estado === valor,
      },
    },
    comparadores: { nota: (a, b) => comparar(a.nota, b.nota) },
  });
}

describe('listaLocal', () => {

  it('busca sin distinguir mayúsculas ni tildes', () => {
    const lista = crear();
    (lista.filtros()[0] as any).alCambiar('ALVARO');
    expect(lista.visibles().map(f => f.nombre)).toEqual(['Álvaro']);
  });

  it('combina la búsqueda con los filtros desplegables', () => {
    const lista = crear();
    (lista.filtros()[1] as any).alCambiar('a');
    expect(lista.total()).toBe(2);
    (lista.filtros()[0] as any).alCambiar('carl');
    expect(lista.visibles().map(f => f.nombre)).toEqual(['Carlos']);
  });

  it('ordena y deja los valores vacíos al final en ascendente', () => {
    const lista = crear();
    lista.ordenar({ campo: 'nota', direccion: 'asc' });
    expect(lista.visibles().map(f => f.nota)).toEqual([3, 7, null]);
    lista.ordenar({ campo: 'nota', direccion: 'desc' });
    expect(lista.visibles().map(f => f.nota)).toEqual([null, 7, 3]);
  });

  it('limpiar filtros devuelve todas las filas y distingue "vacío" de "sin coincidencias"', () => {
    const lista = crear();
    (lista.filtros()[0] as any).alCambiar('zzz');
    expect(lista.total()).toBe(0);
    expect(lista.sinFiltrar()).toBe(3);
    lista.limpiarFiltros();
    expect(lista.total()).toBe(3);
  });

  it('recorta la página pedida cuando el resultado se acorta', () => {
    const origen = signal(Array.from({ length: 25 }, (_, i) => ({ nombre: 'n' + i, estado: 'a', nota: i })));
    const lista = crear(origen as any);
    lista.irA(2);
    expect(lista.pagina()).toBe(2);
    origen.set(origen().slice(0, 5));
    expect(lista.pagina()).toBe(0);
  });
});
