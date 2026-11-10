FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
# 소스만 바뀌었을 때 의존성을 다시 받지 않도록 빌드 설정을 먼저 복사한다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar
EXPOSE 8080
# preferIPv4Stack 은 build.gradle 의 bootRun 과 같은 이유 — IPv6 로만 해석되면 Gemini 호출이 죽는다.
ENTRYPOINT ["java", "-Djava.net.preferIPv4Stack=true", "-jar", "app.jar"]
