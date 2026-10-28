
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
FROM bellsoft/liberica-openjdk-alpine:21

WORKDIR /app

COPY --from=build /app/target/*.jar /app/app.jar

ENV PORT=8080

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
