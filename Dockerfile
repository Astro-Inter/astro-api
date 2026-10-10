FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/astro-api-0.0.1-SNAPSHOT.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60.0 -XX:+ExitOnOutOfMemoryError"
USER 10001:10001
EXPOSE 10000
CMD ["sh", "-c", "exec java -Dserver.address=0.0.0.0 -Dserver.port=${PORT:-10000} -jar app.jar"]
