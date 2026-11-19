FROM openjdk:11-jdk
 
# 时区修改为东八区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone
 
WORKDIR /gateway-mgt
ARG PACKAGE_PATH=./target/gateway-mgt.jar
ADD ${PACKAGE_PATH:-./} gateway-mgt.jar

EXPOSE 8080

ENV JAVA_OPTS="\
-server \
-Xms256m \
-Xmx512m \
-XX:MetaspaceSize=256m \
-XX:MaxMetaspaceSize=512m"
ENTRYPOINT ["sh","-c","java -Djava.security.egd=file:/dev/./urandom -jar $JAVA_OPTS gateway-mgt.jar"]