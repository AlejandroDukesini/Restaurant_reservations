# ---------- Etapa 1: compilar el frontend (React + Vite + Tailwind) ----------
FROM node:22-alpine AS frontend
WORKDIR /app/site_web

RUN npm install -g pnpm@9

# Se copian primero los manifiestos para aprovechar la cache de Docker:
# si no cambian, no se reinstalan las dependencias en cada build.
COPY site_web/package.json site_web/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY site_web/ ./
RUN pnpm build

# ---------- Etapa 2: compilar el backend (Kotlin + Spring Boot) ----------
# La imagen oficial de Gradle trae la misma version que el wrapper (8.6) y evita
# descargar la distribucion en cada build.
FROM gradle:8.6-jdk21 AS backend
WORKDIR /app

COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src

# El build del frontend se incrusta en el JAR: Spring Boot sirve estatico
# desde classpath:/static/, asi que front y API quedan en el mismo origen.
COPY --from=frontend /app/site_web/dist ./src/main/resources/static

RUN gradle bootJar --no-daemon

# ---------- Etapa 3: imagen final de ejecucion ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Principio de minimo privilegio: sin esto el proceso Java corre como root y
# cualquier ejecucion de codigo en la aplicacion hereda root dentro del
# contenedor, lo que facilita la persistencia y el escape hacia el host.
RUN addgroup -S spring && adduser -S -G spring -h /app spring

COPY --from=backend --chown=spring:spring /app/build/libs/*.jar app.jar

# El generador de sitios escribe en ./generated-websites bajo el WORKDIR.
RUN mkdir -p /app/generated-websites && chown -R spring:spring /app

USER spring

# MaxRAMPercentage deja que la JVM se ajuste al limite de memoria del contenedor
# en lugar de asumir la RAM del host.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

EXPOSE 8081
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
