FROM eclipse-temurin:21-jre-jammy

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -Dfile.encoding=UTF-8" \
    SPRING_PROFILES_ACTIVE=prod

RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

WORKDIR /app

COPY ruoyi-admin.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar --spring.profiles.active=$SPRING_PROFILES_ACTIVE"]
