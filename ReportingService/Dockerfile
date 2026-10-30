
# Etapa 1: compilar la aplicación
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

RUN chmod +x mvnw

COPY src ./src

RUN ./mvnw -B clean package

# Etapa 2: ejecutar únicamente la aplicación
# JasperReports mide el texto con AWT: necesita un JRE glibc con fontconfig y al menos una fuente del sistema.
# bellsoft/liberica-openjdk-alpine:21 trae un JDK glibc sobre Alpine (musl): AWT no puede usar el fontconfig del
# sistema y todo Font.createFont falla ("Fontconfig head is null" -> "Problem reading font data").
# eclipse-temurin:21-jre (Ubuntu) ya incluye fontconfig y fonts-dejavu-core.
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/*.jar /app/app.jar

ENV PORT=8080
ENV JAVA_TOOL_OPTIONS="-Djava.awt.headless=true"

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
