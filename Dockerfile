FROM registry.access.redhat.com/ubi9/openjdk-17:1.18-3 as build
LABEL authors="Eric Li"

USER root

ARG username
ARG password

WORKDIR /api

COPY src /api/src
COPY pom.xml checkstyle.xml /api/

RUN cd /etc/maven \
    && sed -i "124 a <server><id>github<\/id><username>${username}<\/username><password>${password}<\/password><\/server>" settings.xml \
    && sed -i '219 a <profile><id>github<\/id><repositories><repository><id>github<\/id><url>https:\/\/maven.pkg.github.com\/Slenergy-Industry-and-Commerce\/Slenergy-Repo<\/url><\/repository><\/repositories><\/profile>' settings.xml \
    && sed -i '257 a <activeProfiles><activeProfile>github<\/activeProfile><\/activeProfiles>' settings.xml \
    && cd /api \
    && mvn clean package assembly:single

FROM registry.access.redhat.com/ubi9/openjdk-17-runtime:1.18-3

USER root

COPY --from=build /api/target/gateway-api-1.0-SNAPSHOT-jar-with-dependencies.jar /data/

VOLUME ["/data/sqlite", "/data/config", "/data/addr", "/data/backup/mmcblk1p1/", "/data/logs"]

EXPOSE 8099

ENTRYPOINT ["java", "-jar", "/data/gateway-api-1.0-SNAPSHOT-jar-with-dependencies.jar", "-cfp", "/data/config/config.json", "-sp", "/data/config/config_schema.json"]
