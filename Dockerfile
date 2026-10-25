# syntax=docker/dockerfile:1

# Build stage: the Maven wrapper pins Maven 3.9.9, so only a JDK is needed.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY src src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -DskipTests package && cp target/*.jar /app.jar

# Runtime stage: JRE only, non-root.
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /app.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
