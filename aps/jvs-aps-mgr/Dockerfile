FROM alibabadragonwell/dragonwell:17

MAINTAINER jvs <www.bctools.com>

RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime
RUN echo 'Asia/Shanghai' > /etc/timezone

ADD ./target/*.jar /app/app.jar
ENV JAVA_OPTS=""
EXPOSE 31010
ENTRYPOINT ["sh","-c","java   $JAVA_OPTS -jar /app/app.jar"]




