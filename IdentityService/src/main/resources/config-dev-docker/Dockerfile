FROM bellsoft/liberica-openjdk-alpine:21

WORKDIR /app

COPY IdentityService.jar /app/IdentityService.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "IdentityService.jar"]