FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY asset-service ./asset-service
COPY warehouse-service ./warehouse-service
COPY workorder-service ./workorder-service
COPY preservation-service ./preservation-service
COPY telemetry-service ./telemetry-service
ARG SERVICE
RUN test -n "$SERVICE" && mvn -B -pl "$SERVICE" -am -DskipTests package && cp "$SERVICE/target/$SERVICE-0.1.0-SNAPSHOT.jar" /workspace/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
EXPOSE 8081 8082 8083 8084 8085
ENTRYPOINT ["java","-jar","/app/app.jar"]
