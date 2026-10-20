# ---------- Stage 1: Maven build ----------
# 基础镜像固定 digest：官方 tag 一更新就要重新下载，国内镜像源常拉不动，部署会卡到超时。
# 固定后直接用两台服务器构建缓存里的版本；升级 JDK 时手动换 digest。
FROM maven:3.9-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320 AS build

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
FROM eclipse-temurin:21-jre-jammy@sha256:bce52ea7da1f72e6bf5bec505e63b6eb55ba79ad1226903579f77eab1a80139a

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
