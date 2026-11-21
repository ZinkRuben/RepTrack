# RepTrack

Edzéskövető és elemző webalkalmazás (BME Szoftverarchitektúrák házi feladat, 2026 ősz).
Java 21, Spring Boot 4.1, Spring Data JPA, Thymeleaf, PostgreSQL, Flyway.

## Fejlesztői környezet

- JDK 21 (`export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`)
- Docker (a PostgreSQL-hez és a Testcontainers tesztekhez)
- Maven nem kell, a projekt Maven wrappert (`./mvnw`) tartalmaz

## Indítás

```bash
docker-compose up -d        # PostgreSQL (localhost:5432, reptrack/reptrack)
./mvnw spring-boot:run      # alkalmazás: http://localhost:8080
```

### Első adminisztrátor

Induláskor jön létre, ha még nincs adminisztrátor, a `REPTRACK_ADMIN_EMAIL`, `REPTRACK_ADMIN_PASSWORD`
(és opcionálisan `REPTRACK_ADMIN_NAME`) környezeti változókból. Helyben a legegyszerűbb a `.env` fájl, amely nem kerül
a repóba:

```bash
cp .env.example .env        # majd írd be a saját e-mail-címed és egy erős jelszót
./mvnw spring-boot:run      # a naplóban: "Adminisztrátori fiók létrehozva: …"
```

Ha már van adminisztrátor, a változókat figyelmen kívül hagyja (a jelszavát sem írja át), így létrehozás után a jelszó
törölhető a `.env`-ből. Hibás adatnál vagy ha a cím egy meglévő fiókhoz tartozik, az alkalmazás nem indul el.

## Tesztek

```bash
./mvnw verify               # integrációs tesztek Testcontainers-szel (fut a Docker)
```

## Rétegek (`hu.reptrack`)

| Csomag | Réteg |
|---|---|
| `web` | GUI: controllerek, Thymeleaf nézetek (csak DTO-kkal dolgozik) |
| `service` | BLL: használati esetek, tranzakciók, tulajdonos-ellenőrzés |
| `rules` | PR-felismerés, túlterhelési szabályok, statisztikai számítások |
| `repository` | DAL: Spring Data JPA repository-k |
| `domain` | JPA entitások |
| `dto` | DTO-k, enumok, kivételek |
| `config` | Security és egyéb konfiguráció |

A séma a `src/main/resources/db/migration` alatti Flyway migrációkból épül fel.

## Csapatmunka

- A `main` ág védett: minden változtatás külön ágon készül, beolvasztás előtt pull request, amit egy másik tag átnéz.
- Ágnév: `feature/<rövid-leírás>`.
