// Sustituye el marcador de la URL de la API en la web ya compilada.
//
// environment.prod.ts no lleva la URL escrita (así no hay que recompilar para cambiar de
// entorno): lleva un marcador que se rellena al construir. El Dockerfile lo hace con sed;
// Vercel construye sin pasar por el Dockerfile, así que lo hace este script.
//
// Por defecto "/api": la misma dirección que la web, que vercel.json reenvía a la API.
// Para otra API: API_URL=https://otra.api/api npm run build:vercel
import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const MARCADOR = 'REEMPLAZAR_CON_LA_URL_DE_LA_API';
const carpeta = 'dist/edu-vibe-front/browser';
const url = process.env.API_URL || '/api';

let sustituidos = 0;

const recorrer = (dir) => {
  for (const nombre of readdirSync(dir)) {
    const ruta = join(dir, nombre);
    if (statSync(ruta).isDirectory()) {
      recorrer(ruta);
    } else if (ruta.endsWith('.js')) {
      const contenido = readFileSync(ruta, 'utf8');
      if (contenido.includes(MARCADOR)) {
        writeFileSync(ruta, contenido.replaceAll(MARCADOR, url));
        sustituidos++;
      }
    }
  }
};

recorrer(carpeta);

if (sustituidos === 0) {
  console.error(`No se ha encontrado el marcador ${MARCADOR} en ${carpeta}: la web saldría sin URL de API.`);
  process.exit(1);
}
console.log(`URL de la API (${url}) inyectada en ${sustituidos} fichero(s).`);
