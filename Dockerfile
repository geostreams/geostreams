# ----------------------------------------------------------------------
# BUILD DISTRIBUTION IN A DOCKER CONTAINER
# ----------------------------------------------------------------------
FROM openjdk:11.0.1-jdk as geostreams-build
#FROM java:jre-alpine as geostreams-build

# Env variables
ENV SCALA_VERSION 2.12.7
ENV SBT_VERSION 1.2.6

WORKDIR /src

# install libraries (hopefully cached)
COPY sbt* /src/
COPY build.sbt /src/
COPY project /src/project
RUN ./sbt update

# compile geostreams
COPY conf/reference.conf /src/conf/application.conf
COPY conf/routes /src/conf/routes
COPY conf/logback.xml /src/conf/logback.xml
COPY conf/messages /src/conf/messages
COPY conf/messages.en /src/conf/messages.en
COPY public /src/public/
COPY app /src/app/
RUN ./sbt dist && unzip -q target/universal/geostreams-*.zip \
    && mv geostreams-* geostreams \
    && mkdir -p geostreams/logs

# ----------------------------------------------------------------------
# BUILD GEOSTREAMS IMAGE
# ----------------------------------------------------------------------
FROM openjdk:11.0.1-jdk
#FROM java:jre-alpine

# add bash
#RUN apk add --no-cache bash curl

# Install sbt
RUN apt-get update && apt-get install --no-install-recommends -y postgresql-client

# expose some properties of the container
EXPOSE 9000

# working directory
WORKDIR /home/geostreams

# customization including data
# VOLUME /home/geostreams/custom /home/geostreams/data

# copy the build file, this requires sbt dist to be run (will be owned by root)
COPY --from=geostreams-build /src/geostreams /home/geostreams/
COPY geostreams.sql /home/geostreams/data/
COPY docker/geostreams.sh docker/entrypoint.sh /home/geostreams/bin/
RUN chmod +x /home/geostreams/bin/geostreams.sh /home/geostreams/bin/entrypoint.sh

# command to run when starting docker
ENTRYPOINT ["/home/geostreams/bin/entrypoint.sh"]
CMD ["server"]