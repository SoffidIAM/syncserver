FROM ubuntu:20.04

RUN apt-get update && apt-get dist-upgrade -y && apt-get install -y openjdk-11-jre-headless

RUN apt-get install -y nmap

RUN DEBIAN_FRONTEND=noninteractive apt-get install -y locales

RUN sed -i -e 's/# en_US.UTF-8 UTF-8/en_US.UTF-8 UTF-8/' /etc/locale.gen && \
    dpkg-reconfigure --frontend=noninteractive locales && \
    update-locale LANG=en_US.UTF-8

RUN locale-gen

RUN apt install -y libcap2-bin

RUN echo "Adding sync server ${pom.version} "
RUN groupadd soffid --gid 10000
RUN useradd soffid --gid 10000 --uid 10000
ADD target/Soffid-sync.tar.gz /opt/soffid/
COPY src/main/docker/start.sh /opt/soffid/iam-sync/start.sh

ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8

RUN chown -R soffid /opt/soffid/iam-sync/
EXPOSE 760

RUN setcap 'cap_net_bind_service=+ep' /usr/lib/jvm/java-11-openjdk-amd64/bin/java

VOLUME  /opt/soffid/iam-sync/conf
ENTRYPOINT /opt/soffid/iam-sync/start.sh