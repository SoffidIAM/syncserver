FROM ubuntu:16.04

RUN apt-get update && apt-get dist-upgrade -y && apt-get install -y openjdk-8-jre-headless

# RUN apt-get  install -y mysql-client

RUN echo "Adding sync server ${pom.version} "
ADD target/install4j/SoffidIAMSync_unix_nojre.tar.gz /opt/soffid/
COPY src/main/docker/start.sh /opt/soffid/iam-sync/start.sh

EXPOSE 760

VOLUME  /opt/soffid/iam-sync/conf
ENTRYPOINT /opt/soffid/iam-sync/start.sh