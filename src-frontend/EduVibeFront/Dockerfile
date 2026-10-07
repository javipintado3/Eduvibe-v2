# Imagen 
FROM node:20.9.0 as build
# Directorio
WORKDIR /app
# Copia los archivos menos el dockerfile
COPY . .
# Instala las dependencias
RUN npm install
# Construye la aplicación Angular
RUN npm run build --prod
# environment.prod.ts lleva un marcador en lugar de la URL de la API: se sustituye
# aquí, al construir la imagen. Por defecto "/api" (misma dirección que el frontend,
# que nginx reenvía a la API); con otra API pasa --build-arg API_URL=https://...
ARG API_URL=/api
RUN grep -rl "REEMPLAZAR_CON_LA_URL_DE_LA_API" dist | xargs sed -i "s|REEMPLAZAR_CON_LA_URL_DE_LA_API|${API_URL}|g"
# Usa una imagen de Nginx para servir la aplicación Angular
FROM nginx:stable-alpine
# Copia los archivos 
COPY --from=build /app/dist/edu-vibe-front/browser /usr/share/nginx/html
# Sin esta configuración nginx no sabe volver a index.html y cualquier enlace
# directo de la aplicación (invitación, recuperar contraseña...) da 404
COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 80
# Comando para iniciar Nginx
CMD ["nginx", "-g", "daemon off;"]
