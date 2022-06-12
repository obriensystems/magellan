#FROM anapsix/alpine-java:jre8
FROM openjdk:11

ARG USERVICE_HOME=/opt/app/

# Build up the deployment folder structure
RUN mkdir -p $USERVICE_HOME

ADD magellan-nbi/target/magellan-nbi-*.jar $USERVICE_HOME/ROOT.jar
#ADD startService.sh $USERVICE_HOME/bin/

EXPOSE 8080
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/opt/app/ROOT.jar"]

#CMD ["/opt/app/bin/startService.sh"]