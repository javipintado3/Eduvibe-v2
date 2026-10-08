# Etapa de construcción
FROM maven:3.9-eclipse-temurin-17 AS builder
COPY src /usr/src/app/src
COPY pom.xml /usr/src/app
RUN mvn -f /usr/src/app/pom.xml clean package -DskipTests

# Etapa de producción
FROM eclipse-temurin:17-jre

# Variables de entorno
ENV BBDD_HOST="db"
ENV BBDD_NAME="eduvibe"
ENV BBDD_PORT=5432
ENV APP_PORT=9090
ENV LOG_LEVEL="INFO"
ENV MAIL_USERNAME=""
ENV MAIL_PASSWORD=""

# Volumen para datos temporales y archivos multimedia
VOLUME /tmp
VOLUME /mediafiles
RUN mkdir -p /mediafiles

# Puerto expuesto
EXPOSE 9090

# Copiar el archivo JAR construido de la etapa de construcción
# El comodín evita tocar el Dockerfile en cada versión (solo hay un .jar ejecutable en target)
COPY --from=builder /usr/src/app/target/eduvibe-api-*.jar /app/app.jar

# Establecer directorio de trabajo

# Comando de inicio
ENTRYPOINT ["java", "-jar", "/app/app.jar"]


