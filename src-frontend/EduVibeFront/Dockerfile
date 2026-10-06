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
