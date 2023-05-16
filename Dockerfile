FROM registry.cn-qingdao.aliyuncs.com/metersphere/alpine-openjdk11-jre

LABEL maintainer="FIT2CLOUD <support@fit2cloud.com>"

ARG MS_VERSION=dev
ARG DEPENDENCY=target/dependency

COPY ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY ${DEPENDENCY}/META-INF /app/META-INF
COPY ${DEPENDENCY}/BOOT-INF/classes /app


RUN mv /app/lib/ms-jmeter-core-*.jar /app/lib/ms-jmeter-core.jar
RUN mv /app/jmeter /opt/
RUN addgroup -S docker && addgroup appuser docker

ENV JAVA_CLASSPATH=/app:/app/lib/ms-jmeter-core.jar:/opt/jmeter/lib/ext/*:/app/lib/*
ENV JAVA_MAIN_CLASS=io.metersphere.Application
ENV AB_OFF=true
ENV MS_VERSION=${MS_VERSION}
ENV RUN_AS_NON_ROOT_USER=1
ENV JAVA_OPTIONS="-Dfile.encoding=utf-8 -Djava.awt.headless=true --add-opens java.base/jdk.internal.loader=ALL-UNNAMED"

CMD ["/deployments/run-java.sh"]
