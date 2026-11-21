# syntax=docker/dockerfile:1
#
# RepTrack konténerkép, két lépésben:
#   1. build:   JDK + Maven wrapper, a futtatható JAR elkészítése, majd rétegekre bontása
#   2. runtime: csak JRE, nem root felhasználóval; HTTPS a 8443-as porton (docker profil, lásd
#               application-docker.properties), az önaláírt tanúsítványt első induláskor a docker/entrypoint.sh készíti.
#
# A tesztek itt nem futnak (Testcontainers-hez Docker kellene); futtasd őket előtte: ./mvnw verify
# Használat: docker compose up -d --build   (lásd docker-compose.yml és README)

# ---------- 1. lépés: fordítás ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# előbb csak a függőségek: ha a pom.xml nem változik, ez a réteg (és a Maven-gyorsítótár) újrahasznosul
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src/ src/
# a JAR neve verziófüggetlen (application.jar), a kibontás ezt a nevet viszi tovább
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
    && cp target/reptrack-*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---------- 2. lépés: futtatás ----------
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S reptrack \
    && adduser -S -G reptrack -h /app reptrack \
    && mkdir -p /certs \
    && chown reptrack:reptrack /certs

WORKDIR /app

# a ritkán változó rétegek előre, így egy kódváltozás után csak az utolsó réteg cserélődik
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./
COPY --chmod=755 docker/entrypoint.sh /usr/local/bin/reptrack-entrypoint

USER reptrack

ENV SPRING_PROFILES_ACTIVE=docker \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

# 8443: HTTPS, 8080: csak átirányítás HTTPS-re
EXPOSE 8443 8080

# itt marad meg a tanúsítvány és a jelszava az újraindítások között
VOLUME /certs

ENTRYPOINT ["reptrack-entrypoint"]
