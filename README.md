# RepTrack

Edzéskövető és elemző webalkalmazás (BME Szoftverarchitektúrák házi feladat, 2026 ősz).
Java 21, Spring Boot 4.1, Spring Data JPA, Thymeleaf, PostgreSQL, Flyway.

## Fejlesztői környezet

- JDK 21 (`export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`)
- Docker (a PostgreSQL-hez és a Testcontainers tesztekhez)
- Maven nem kell, a projekt Maven wrappert (`./mvnw`) tartalmaz

## Indítás

### Fejlesztéshez (helyi JDK, sima HTTP)

```bash
docker compose up -d db     # csak a PostgreSQL (localhost:5432, reptrack/reptrack)
./mvnw spring-boot:run      # alkalmazás: http://localhost:8080
```

### Teljes rendszer Dockerben (HTTPS)

```bash
docker compose up -d --build   # alkalmazás + adatbázis: https://localhost:8443
```

- A kép többlépcsős `Dockerfile`-ból épül (JDK-val fordít, JRE-vel fut, nem root felhasználóval). A tesztek a
  képépítéskor nem futnak, előtte: `./mvnw verify`.
- **HTTPS önaláírt tanúsítvánnyal:** első induláskor a konténer készíti el (véletlen jelszóval) a `reptrack-certs`
  kötetbe, így újraindítás után is ugyanaz marad. A böngésző első alkalommal figyelmeztet: el kell fogadni.
  Tanúsítvány és jelszó nem kerül a repóba.
- A `http://localhost:8080` a HTTPS-címre irányít át. Ha fut mellette a fejlesztői `./mvnw spring-boot:run` is, az is
  a 8080-at használná: ilyenkor az egyiket állítsd le, vagy adj meg más portot a `.env`-ben (`HTTP_PORT`, `HTTPS_PORT`).
- Beállítások a `.env`-ből: az első admin (`REPTRACK_ADMIN_*`, lásd lent), a portok, és az adatbázis jelszava
  (`DB_PASSWORD`; csak az adatbázis első létrehozása előtt változtatható).
- Leállítás: `docker compose down` (az adatok és a tanúsítvány megmaradnak), mindent törölve: `docker compose down -v`.

### Első adminisztrátor

Induláskor jön létre, ha még nincs adminisztrátor, a `REPTRACK_ADMIN_EMAIL`, `REPTRACK_ADMIN_PASSWORD`
(és opcionálisan `REPTRACK_ADMIN_NAME`) környezeti változókból. Helyben a legegyszerűbb a `.env` fájl, amely nem kerül
a repóba:

```bash
cp .env.example .env        # majd írd be a saját e-mail-címed, és a CHANGE-ME helyére egy erős jelszót
./mvnw spring-boot:run      # a naplóban: "Adminisztrátori fiók létrehozva: …"
```

Ha már van adminisztrátor, a változókat figyelmen kívül hagyja (a jelszavát sem írja át), így létrehozás után a jelszó
törölhető a `.env`-ből. Hibás adatnál vagy ha a cím egy meglévő fiókhoz tartozik, az alkalmazás nem indul el.

A `.env.example` jelszava csak mintaérték (`CHANGE-ME`). Ha ez marad a `.env`-ben, és még nincs adminisztrátor, az
alkalmazás nem indul el, és a hibaüzenet megmondja, melyik változót kell javítani (Dockerben: `docker compose logs app`).
Így a mintajelszó nem kerülhet élesbe.

## Tesztek

```bash
./mvnw verify               # integrációs tesztek Testcontainers-szel (fut a Docker)
```

## Gyakorlatképek

A gyakorlatválasztó ablak (az edzés és a sablon űrlapján) a gyakorlatokat képpel mutatja. A képek a
`src/main/resources/static/images/exercises/` mappában vannak; ha egy gyakorlathoz nincs kép, a kategória ikonja
látszik.

**Elnevezés.** A fájl neve a gyakorlat képkulcsa (`exercises.image_key`) és a kiterjesztés (.jpg, .webp vagy .png).
A kulcs a név ékezet nélkül, kisbetűvel, a szavak kötőjellel összekötve: Ferde pados fekvenyomás →
`ferde-pados-fekvenyomas.jpg`. Kivétel a Futás, amelynek a képe `szabadteri-futas.jpg`.

**Hol találod a kulcsokat.** A beépített 50 gyakorlat kulcsát a
`src/main/resources/db/migration/V7__exercise_images.sql` sorolja fel. A futó adatbázisból is lekérdezhetők:

```bash
docker exec -it reptrack-db psql -U reptrack -d reptrack -c "select name, image_key from exercises where owner_id is null order by name"
```

Az adminisztrátor által később felvett közös gyakorlat a létrehozásakor kap kulcsot a nevéből, ugyanezzel a
szabállyal (`ExerciseService.imageKeyFor`); az átnevezés nem változtat rajta. A saját gyakorlatoknak nincs képe.

**Új kép felvétele.** Adatbázist nem kell módosítani.

1. Készítsd el a képet az alábbi prompttal: négyzetes, kb. 512×512 px, fehér háttérrel.
2. Nevezd el a gyakorlat képkulcsával (pl. `labtolas.jpg`), és tedd a fenti mappába.
3. Építsd újra az alkalmazást (Dockerben `docker compose up -d --build`; helyben `./mvnw spring-boot:run`).
   Induláskor a napló kiírja, hány képet talált: „Gyakorlatképek: N kép …”.
4. Futtasd a teszteket (`./mvnw verify`): az `ExerciseImagesTest` bukik, ha egy fájl neve egyik gyakorlat kulcsához
   sem illik (például elgépelés miatt).

**Hogyan készültek a képek.** A Google Gemini képgenerátorával, minden gyakorlathoz ugyanazzal a prompttal, hogy
egységes legyen a stílusuk. A `<name-of-exercise-by-key>` helyére az adott gyakorlat neve került (a képkulcs alapján).
Az új képeket is ezzel a prompttal érdemes készíteni:

```text
Flat 2D vector instructional fitness illustration, in the style of a gym weight machine instruction placard. A minimalist, faceless athletic mannequin performing a "<name-of-exercise-by-key>". Show two overlapping poses to demonstrate range of motion: a semi-transparent, light grey ghosted silhouette in the start position and a solid, dark charcoal figure in the end position. Include a clean, curved directional arrow showing the path of movement. Isolated on a solid white background, crisp lines, UI icon style, no text, no realistic shading.
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
