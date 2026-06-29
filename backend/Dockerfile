FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/cloudshop-backend-0.1.0.jar app.jar

EXPOSE 3000

ENTRYPOINT ["java", "-jar", "app.jar", "--server.port=3000"]

