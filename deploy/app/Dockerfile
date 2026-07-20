FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY backend/.mvn backend/.mvn
COPY backend/mvnw backend/pom.xml backend/
RUN chmod +x backend/mvnw
RUN cd backend && ./mvnw -q -DskipTests dependency:go-offline

COPY backend/src backend/src
RUN cd backend && ./mvnw -q -DskipTests package

FROM eclipse-temurin:25-jre-noble
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && groupadd --system zija && useradd --system --gid zija --home-dir /app zija

WORKDIR /app
COPY --from=build --chown=zija:zija /workspace/backend/target/*.jar /app/zija.jar

USER zija
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/zija.jar"]
