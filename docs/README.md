# Documentación de EduVibe

Índice de lo que hay en esta carpeta. La presentación del proyecto, cómo arrancarlo y su configuración están en el [README principal](../README.md).

## Qué hay aquí

| Documento | Qué contiene |
|---|---|
| [Memoria del proyecto (PDF)](2023-2024-iesAlixar-daw2-JavierPintadoNavarro-EduVibe.pdf) | Memoria del proyecto de fin de ciclo (IES Alixar, DAW2, curso 2023-2024) |
| [Guía de despliegue gratuito](DESPLIEGUE-RENDER.md) | Paso a paso para publicar EduVibe con Neon (base de datos), Render (API) y Vercel (web) |
| [`screenshots/`](screenshots) | Las capturas que usa el README principal |

## Apartados de la memoria

La memoria recoge: portada, índice, introducción, identificación de las necesidades, comparativa con alternativas del mercado, justificación, stack tecnológico, modelo de datos, prototipo de la aplicación web, definición de la API REST, manual de despliegue y postmortem con conclusiones.

> La memoria describe la primera versión del proyecto. La aplicación ha evolucionado desde entonces (esquema en PostgreSQL con migraciones Flyway, exámenes, rúbricas, registro con aprobación, etc.), así que para el estado actual manda el README principal.

## Datos de ejemplo

Los datos de demostración actuales no están en esta carpeta: son una migración repetible de Flyway en `src-api/EduvibeBackend/src/main/resources/db/demo`, y se cargan solos al arrancar con Docker Compose.
