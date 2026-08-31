FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /application

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY src/main src/main

RUN chmod +x gradlew

RUN ./gradlew bootJar --no-daemon -x test -x integrationTest -x jmh -x gatlingRun

RUN java -Djarmode=tools -jar build/libs/*.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre-alpine
WORKDIR /application

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser:appgroup

COPY --from=builder /application/extracted/dependencies/ ./
COPY --from=builder /application/extracted/spring-boot-loader/ ./
COPY --from=builder /application/extracted/snapshot-dependencies/ ./
COPY --from=builder /application/extracted/application/ ./

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:+EnableDynamicAgentLoading", "org.springframework.boot.loader.launch.JarLauncher"]
