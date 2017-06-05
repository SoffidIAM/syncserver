FROM ubuntu:16.04

RUN apt-get update && apt-get dist-upgrade -y && apt-get install -y openjdk-8-jre-headless

# RUN apt-get  install -y mysql-client

RUN echo "Adding sync server ${pom.version} "
COPY target/install4j/iam-sync.tar.gz Soffid-iamsync.tar.gz
RUN mkdir -p /opt/soffid && cd /opt/soffid && tar -xzf /Soffid-iamsync.tar.gz && rm /Soffid-iamsync.tar.gz
COPY src/main/docker/start.sh /opt/soffid/iam-sync/start.sh

EXPOSE 760

VOLUME  /opt/soffid/iam-sync/conf
ENTRYPOINT /opt/soffid/iam-sync/start.sh