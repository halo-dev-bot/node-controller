FROM registry.cn-qingdao.aliyuncs.com/metersphere/alpine-openjdk17-jre

LABEL maintainer="FIT2CLOUD <support@fit2cloud.com>"

ARG MS_VERSION=dev
ARG DEPENDENCY=target/dependency

COPY ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY ${DEPENDENCY}/META-INF /app/META-INF
COPY ${DEPENDENCY}/BOOT-INF/classes /app

RUN mv /app/lib/metersphere-jmeter-core-*.jar /app/lib/metersphere-jmeter-core.jar
RUN mv /app/jmeter /opt/
RUN addgroup -g 101 -S docker && addgroup appuser docker

ENV JAVA_CLASSPATH=/app:/app/lib/metersphere-jmeter-core.jar:/opt/jmeter/lib/ext/*:/app/lib/*
ENV JAVA_MAIN_CLASS=io.metersphere.Application
ENV AB_OFF=true
ENV MS_VERSION=${MS_VERSION}
ENV JAVA_OPTIONS="-Dfile.encoding=utf-8 -Djava.awt.headless=true -Dpolyglot.js.nashorn-compat=true -Dpolyglot.log.file=/opt/metersphere/logs/node-controller/info.log --add-opens java.base/jdk.internal.loader=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED -Dnashorn.args=--no-deprecation-warning"

CMD ["/deployments/run-java.sh"]
