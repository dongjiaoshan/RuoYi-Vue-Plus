# syntax=docker/dockerfile:1.7

# ---------- Stage 1: Maven build ----------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml ./
COPY ruoyi-common ./ruoyi-common
COPY ruoyi-modules ./ruoyi-modules
COPY ruoyi-extend ./ruoyi-extend
COPY ruoyi-admin ./ruoyi-admin

# BuildKit cache mount: Maven .m2 持久化在宿主机，第二次起 build 跳过依赖下载
RUN --mount=type=cache,target=/root/.m2 \
    MAVEN_OPTS="-Xmx1g -XX:+UseG1GC" \
    mvn -B -DskipTests -pl ruoyi-admin -am clean package

# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:21-jre-jammy

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -Dfile.encoding=UTF-8" \
    SPRING_PROFILES_ACTIVE=prod

# 装 wget（healthcheck 用）+ 时区数据
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/* \
    && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime \
    && echo $TZ > /etc/timezone

WORKDIR /app

COPY --from=build /build/ruoyi-admin/target/ruoyi-admin.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar --spring.profiles.active=$SPRING_PROFILES_ACTIVE"]
