FROM ubuntu:16.04

RUN apt-get update && apt-get dist-upgrade -y && apt-get install -y openjdk-8-jre-headless

# RUN apt-get  install -y mysql-client

RUN DEBIAN_FRONTEND=noninteractive apt-get install -y locales

RUN sed -i -e 's/# en_US.UTF-8 UTF-8/en_US.UTF-8 UTF-8/' /etc/locale.gen && \
    dpkg-reconfigure --frontend=noninteractive locales && \
    update-locale LANG=en_US.UTF-8

RUN locale-gen

RUN echo "Adding sync server ${pom.version} "
ADD target/install4j/SoffidIAMSync_unix_nojre.tar.gz /opt/soffid/
COPY src/main/docker/start.sh /opt/soffid/iam-sync/start.sh

ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8

EXPOSE 760

VOLUME  /opt/soffid/iam-sync/conf
ENTRYPOINT /opt/soffid/iam-sync/start.sh