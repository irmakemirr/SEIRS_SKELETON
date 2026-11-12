# ---- build ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY src src
# /root/.gradle (Gradle dağıtımı + bağımlılıklar) BuildKit cache mount'unda kalıcıdır:
# katman önbelleği bozulsa bile tekrar indirilmez.
# Docker VM belleği kısıtlı olabileceği için Kotlin ayrı daemon açmadan, sınırlı tek JVM'de derlenir.
RUN --mount=type=cache,id=radar-gradle,target=/root/.gradle \
    chmod +x gradlew && ./gradlew --no-daemon \
      "-Dorg.gradle.jvmargs=-Xmx1g -XX:MaxMetaspaceSize=384m" \
      -Pkotlin.compiler.execution.strategy=in-process \
      bootJar -x test

# ---- runtime ----
FROM eclipse-temurin:25-jre
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
