# Runtime Dockerfile that expects a built jar in target/
# Build the jar locally first: .\mvnw.cmd -DskipTests package
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar
ENV JAVA_OPTS=""
EXPOSE 80
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]

