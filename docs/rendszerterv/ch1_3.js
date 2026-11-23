// 1–3. fejezet: bevezetés, a rendszer áttekintése, architektúra
module.exports = function (c) {
  const { p, h1, h2, bullets, numbered, note, table, tcap, FIG, spacer } = c;
  const out = [];

  // ------------------------------------------------------------------------------------------------ 1.
  out.push(h1("1. Bevezetés"));
  out.push(h2("1.1 A dokumentum célja és hatóköre"));
  out.push(p("Ez a dokumentum az **Edzéskövető és elemző alkalmazás** (kódneve: **RepTrack**) rendszerterve. A követelményspecifikáció azt írja le, __mit__ tud a rendszer; a rendszerterv azt, __hogyan__ valósítottuk meg: milyen architektúrára épül, milyen rétegekből és komponensekből áll, hogyan működnek a főbb funkciók, hogyan épül fel az adatbázis, és hogyan oldottuk meg a biztonságot, a tesztelést és az üzemeltetést."));
  out.push(p("A dokumentum a **megvalósított** rendszert mutatja be; a forráskód a GitHub-tárolóban érhető el (https://github.com/ZinkRuben/RepTrack). Ahol a megvalósítás eltér a specifikációtól, vagy egy funkció még nem készült el, azt külön jelöljük (2.4. és 11. fejezet)."));
  out.push(p("A dokumentum olvasói a fejlesztők (a rendszer továbbfejlesztéséhez és karbantartásához), a konzulens és az értékelők. Feltételezi a Java, a Spring és a relációs adatbázisok alapfogalmainak ismeretét."));

  out.push(h2("1.2 Kapcsolódó dokumentumok"));
  out.push(...bullets([
    "**Követelményspecifikáció** (Edzéskövető és elemző alkalmazás, 5. változat): a funkciók, a minőségi jellemzők és a használati esetek (UC1–UC16) leírása.",
    "**Telepítési dokumentáció**: a program telepítésének és indításának lépései (külön dokumentum).",
    "**A megvalósított rendszer**: forrás és bináris formában, a beadott csomagban és a GitHub-tárolóban.",
    "**ER-diagram**: a `docs/er-diagram.*` fájlok a tárolóban, a Flyway-migrációkból generálva.",
  ]));

  out.push(h2("1.3 A dokumentum felépítése"));
  out.push(tcap("A fejezetek tartalma"));
  out.push(table(["Fejezet", "Tartalom"], [
    ["2. A rendszer áttekintése", "szereplők, használati esetek és megvalósítási állapotuk, technológiai verem, eltérések a specifikációtól"],
    ["3. Architektúra", "a rétegek, a csomagfüggőségek, a telepítési nézet, egy kérés útja, keresztmetszeti megoldások, tervezési minták"],
    ["4. A rétegek részletesen", "webes réteg, üzleti logika (szolgáltatások és szabályok), adatelérés, domain és DTO, konfiguráció"],
    ["5. Főbb funkciók működése", "szekvenciadiagramok: edzés rögzítése, bejelentkezés, statisztikák, egyidejű módosítás"],
    ["6. Adatbázisterv", "ER-diagram, táblák, megszorítások, tervezési döntések, migrációk"],
    ["7. Biztonság", "hitelesítés, jogosultság, adatvédelem, támadások elleni védelem"],
    ["8. Felhasználói felület és dizájn", "oldaltérkép, a felület elvei"],
    ["9. Tesztelés", "tesztstratégia és a tesztek összetétele"],
    ["10. Fejlesztői környezet és üzemeltetés", "build, indítás, konfiguráció, csapatmunka"],
    ["11–13. és függelék", "ismert korlátok, továbbfejlesztési lehetőségek, összegzés; a függelékben a képletek és definíciók"],
  ], [3000, 6026]));

  out.push(h2("1.4 Rövidítések és fogalmak"));
  out.push(tcap("Rövidítések"));
  out.push(table(["Rövidítés", "Jelentés"], [
    ["GUI", "grafikus (webes) felület, a webes réteg"],
    ["BLL", "üzleti logikai réteg (Business Logic Layer)"],
    ["DAL", "adatelérési réteg (Data Access Layer)"],
    ["DTO", "adatátviteli objektum (Data Transfer Object); a projektben Java rekordok"],
    ["JPA", "Jakarta Persistence, az objektum-relációs leképezés szabványa (megvalósítása: Hibernate)"],
    ["MVC", "Model–View–Controller; a Spring MVC a webes réteg keretrendszere"],
    ["PRG", "Post/Redirect/Get: a módosító kérést átirányítás követi"],
    ["CSRF", "Cross-Site Request Forgery, kéréshamisítás elleni védelem tokennel"],
    ["PR", "egyéni csúcs (personal record)"],
    ["1RM", "egyismétléses maximum, a projektben Epley-képlettel becsülve"],
    ["UC", "használati eset (use case) a specifikációból, pl. UC7"],
  ], [1800, 7226]));

  // ------------------------------------------------------------------------------------------------ 2.
  out.push(h1("2. A rendszer áttekintése"));
  out.push(h2("2.1 Cél és szereplők"));
  out.push(p("A RepTrack egy webalkalmazás, amellyel a felhasználók strukturáltan rögzíthetik edzéseiket (súlyzós, kardió és saját testsúlyos gyakorlatok szettenként), nyomon követhetik a testtömegüket és az edzéscéljaikat, és statisztikák, diagramok segítségével elemezhetik a fejlődésüket. Extra funkcióként a rendszer mentéskor automatikusan felismeri az egyéni csúcsokat (PR), és figyelmeztet a túlterhelés jeleire. Nem helyettesíti az edzői vagy orvosi tanácsadást."));
  out.push(...bullets([
    "**Vendég**: megtekintheti a nyitóoldalt, regisztrálhat és bejelentkezhet.",
    "**Felhasználó (USER)**: a saját edzéseit, profilját, céljait és statisztikáit kezeli. Az adatait más felhasználó nem látja.",
    "**Adminisztrátor (ADMIN)**: a felhasználó minden funkcióját eléri, emellett a közös gyakorlatadatbázist kezeli (UC6); a fiókok kezelése (UC13) tervezett. Az első adminisztrátort a telepítés hozza létre.",
  ]));

  out.push(h2("2.2 Funkciók és megvalósítási állapot"));
  out.push(p("A táblázat a specifikáció használati eseteit (UC1–UC16) és azok megvalósítási állapotát mutatja a dokumentum legutóbbi frissítésekor (2026. november 23.). A „Kész” állapotú funkciók működnek és tesztelve vannak; a „Részben” és „Tervezett” sorokat a 11. fejezet részletezi."));
  out.push(tcap("Használati esetek és megvalósítási állapotuk"));
  out.push(table(["Azon.", "Használati eset", "Állapot", "Megvalósítás"], [
    ["UC1", "Regisztráció, bejelentkezés", "Kész", "`AuthController`, `UserService`, `AccountDetailsService`, `SecurityConfig`"],
    ["UC2", "Profil és célok kezelése", "Kész", "`ProfileController`, `ProfileService`; a túlterhelési küszöbök is a profilban"],
    ["UC3", "Testtömeg rögzítése", "Kész", "`ProfileService`; naponta egy mérés, a profil a legutóbbit mutatja"],
    ["UC4", "Gyakorlatok böngészése, keresése", "Kész", "`ExerciseController`, `ExerciseService`; szűrés névre, kategóriára, izomcsoportra, forrásra (közös/saját) és archiváltságra"],
    ["UC5", "Saját gyakorlat felvétele", "Kész", "`ExerciseController`, `ExerciseService`; létrehozás, módosítás, archiválás, törlés (a használatban lévő gyakorlat nem törölhető)"],
    ["UC6", "Közös gyakorlatadatbázis karbantartása", "Kész", "`AdminExerciseController`, `ExerciseService`; `/admin/exercises/**` (ADMIN), a szolgáltatás `@PreAuthorize`-zal is védett"],
    ["UC7", "Edzés rögzítése", "Kész", "`WorkoutController`, `WorkoutService`; üresen, sablonból vagy korábbi edzés másolataként"],
    ["UC8", "Edzés módosítása, törlése", "Kész", "`WorkoutService`; optimista zárolás"],
    ["UC9", "Edzésnapló böngészése", "Kész", "lapozott, szűrhető lista (időszak, gyakorlat, kategória)"],
    ["UC10", "Statisztikák és diagramok", "Kész", "`StatsController`, `StatisticsService`, `StatisticsCalculator`"],
    ["UC11", "PR-ek megtekintése", "Kész", "`RecordController`, `PersonalRecordService`, `PersonalRecordDetector`"],
    ["UC12", "Túlterhelési figyelmeztetések kezelése", "Kész", "`OverloadService`, `OverloadDetector`, `WarningController`"],
    ["UC13", "Felhasználók kezelése", "Tervezett", "adminisztrációs felület"],
    ["UC14", "Fiók és adatok törlése", "Tervezett", "az adatbázis `ON DELETE CASCADE` kapcsolatai már támogatják"],
    ["UC15", "Edzéssablonok kezelése", "Kész", "`TemplateController`, `TemplateService`; sablon mentése (üresen vagy egy edzésből), szerkesztése, törlése; edzés indítása sablonból"],
    ["UC16", "Eredmények kiértékelése", "Kész", "automatikusan fut az edzés mentésekor, módosításakor és törlésekor"],
  ], [700, 2900, 1100, 4326], { statusCol: 2, size: 19 }));

  out.push(h2("2.3 Technológiai verem"));
  out.push(tcap("A felhasznált technológiák"));
  out.push(table(["Terület", "Technológia", "Megjegyzés"], [
    ["Nyelv, build", "Java 21 (LTS), Maven", "a projekt Maven wrappert (`./mvnw`) tartalmaz, külön Maven nem kell"],
    ["Keretrendszer", "Spring Boot 4.1.3", "Spring Framework 7; moduláris starterek"],
    ["Webes réteg", "Spring MVC, Thymeleaf, `thymeleaf-extras-springsecurity6`", "szerveroldali HTML-előállítás"],
    ["Kliensoldal", "Bootstrap 5.3.3, Bootstrap Icons 1.11.3, Chart.js 4.4.7", "CDN-ről töltődnek; saját stíluslap (`app.css`) és szkript (`app.js`)"],
    ["Üzleti logika", "Spring `@Service`, `@Transactional`, Bean Validation", "tranzakció és validáció a szolgáltatásokon"],
    ["Adatelérés", "Spring Data JPA (Hibernate 7), JPQL, Criteria API", "paraméterezett lekérdezések"],
    ["Adatbázis", "PostgreSQL 16", "fejlesztéskor Docker konténerben"],
    ["Séma-verziózás", "Flyway (V1–V5)", "`ddl-auto=validate`: a Hibernate csak ellenőriz"],
    ["Biztonság", "Spring Security 7", "űrlapos bejelentkezés, BCrypt, CSRF, szerepkör-hierarchia, HTTPS"],
    ["Üzemeltetés", "Docker, Docker Compose", "többlépcsős Dockerfile; az alkalmazás és a PostgreSQL egy paranccsal indul"],
    ["Tesztelés", "JUnit 5, Spring Boot Test, Testcontainers, Spring Security Test", "valódi PostgreSQL a tesztekben"],
    ["Verziókezelés", "Git, GitHub", "feature-ágak, pull request és áttekintés"],
  ], [1700, 3700, 3626], { size: 19 }));

  out.push(h2("2.4 Eltérések a specifikációtól"));
  out.push(p("A fejlesztés során néhány ponton a specifikációtól eltérő megoldást választottunk, vagy egy elem még nem készült el. Az alábbi táblázat ezeket gyűjti össze, indoklással."));
  out.push(tcap("Eltérések a követelményspecifikációtól"));
  out.push(table(["Terület", "Specifikáció", "Megvalósítás", "Indok"], [
    ["Spring Boot", "3-as főverzió", "4.1.3", "az újabb, stabil kiadást választottuk; a rétegeket, a JPA-t és a Security-t nem érinti"],
    ["Diagramok adatai", "JSON végpontok", "a szerver az összesített adatsorokat JSON-ként beágyazza az oldalba", "egyetlen kérés, nincs külön REST-felület; ugyanaz a védelem, mint az oldalé"],
    ["Célhaladás helye", "a statisztikai oldalon", "a „Profil és célok” oldalon, a cél mellett", "a cél és a haladása egy helyen látszik"],
    ["A BLL tesztelése", "Mockito egységtesztek", "szabályok: tiszta egységtesztek; szolgáltatások: integrációs tesztek valódi PostgreSQL-lel", "a szolgáltatások logikája az adatbázishoz (megszorítások, lekérdezések) kötődik, ezt mock nem ellenőrzi"],
    ["Edzés indítása sablonból", "az edzés létrejön, és betölti a tervezett gyakorlatokat és szetteket", "az új edzés űrlapja töltődik ki a sablonból; az edzés a Mentés gombra jön létre", "a félbehagyott indítás nem hagy üres, nyitott edzést a naplóban"],
    ["Demófelhasználó", "néhány hónapnyi mintaadat", "tervezett", "az 50 gyakorlatos alapadatbázis már megvan"],
  ], [1500, 2000, 2900, 2626], { size: 19 }));

  // ------------------------------------------------------------------------------------------------ 3.
  out.push(h1("3. Architektúra"));
  out.push(h2("3.1 Alapelvek"));
  out.push(...bullets([
    "**Egyirányú függőség.** A rétegek csak lefelé hívnak: web → service → repository → domain. A felsőbb réteg ismeri az alsót, fordítva soha. A tényleges függőségeket a forráskód importjaiból generáltuk (2. ábra).",
    "**A réteghatár a DTO.** A webes réteg csak DTO-kat (Java rekordokat) lát; JPA entitás a szolgáltatásokból nem kerül ki. Így az adatbázis szerkezete és a lusta betöltés nem szivárog a nézetekbe, a `spring.jpa.open-in-view` pedig ki van kapcsolva.",
    "**A szabályok tiszta függvények.** A PR-felismerés, a túlterhelési szabályok és a statisztikák a `rules` csomagban vannak, Spring és adatbázis nélkül. Ugyanarra a bemenetre mindig ugyanazt adják, ezért egyszerűen tesztelhetők, és bármikor újraszámolhatók.",
    "**Minden adat a tulajdonosé.** Minden szolgáltatásművelet megkapja a bejelentkezett felhasználó azonosítóját, és csak a saját adatait éri el (4.2).",
    "**Az adatbázis is őrzi az integritást.** A séma kizárólag Flyway-migrációkból épül fel kulcsokkal, CHECK és UNIQUE megszorításokkal; a Hibernate csak ellenőriz.",
    "**Külső konfiguráció.** Az adatbázis-hozzáférés és az első adminisztrátor adatai környezeti változókból jönnek; a tárolóban nincs titok.",
  ]));

  out.push(h2("3.2 A rétegek áttekintése"));
  out.push(p("A szerveroldal klasszikus rétegelt (n-tier) felépítésű. Az 1. ábra a rétegeket, a köztük lévő hívási irányt és a keresztmetszeti elemeket mutatja; a színek a specifikáció ábrájával egyeznek (kék: rétegek, narancs: biztonság, zöld: közös definíciók)."));
  out.push(tcap("A rétegek felelőssége"));
  out.push(table(["Réteg", "Csomag", "Felelősség", "Nem feladata"], [
    ["Webes réteg (GUI)", "`web`", "HTTP-kérések fogadása, űrlapkötés és első ellenőrzés, a modell összeállítása, nézet kiválasztása, átirányítás, hibaoldalak", "üzleti döntés, adatbázis-elérés"],
    ["BLL: szolgáltatások", "`service`", "használati esetek, tranzakciók, validáció, tulajdonos-ellenőrzés, DTO-k előállítása", "HTTP, HTML, SQL"],
    ["BLL: szabályok", "`rules`", "PR-felismerés, túlterhelési szabályok, statisztikai számítások, képletek", "adatbázis, Spring"],
    ["Adatelérés (DAL)", "`repository`, `domain`", "JPA entitások, lekérdezések, lapozás", "üzleti szabályok"],
    ["Adatbázis (DB)", "PostgreSQL", "tárolás és integritás (kulcsok, CHECK, UNIQUE)", "üzleti logika"],
    ["Közös definíciók", "`dto`", "átviteli objektumok, enumok, kivételek", "működési logika"],
    ["Keresztmetszeti", "`config`, Spring Security", "hitelesítés, jogosultság, konfiguráció, indulási feladatok", "–"],
  ], [1700, 1500, 3900, 1926], { size: 19 }));
  out.push(...FIG("architektura", 14.5, "A RepTrack rétegei és keresztmetszeti elemei"));

  out.push(h2("3.3 Csomagszerkezet és függőségek"));
  out.push(p("A 2. ábra a `hu.reptrack` csomagjai közötti függőségeket mutatja. Az ábrát a forráskód `import` utasításaiból generáljuk (`docs/rendszerterv/diagrams.py`), ezért mindig a valós állapotot tükrözi. A zöld szaggatott nyilak a `dto` csomag használatát jelölik."));
  out.push(...FIG("csomagfuggosegek", 15.5, "A csomagok közötti függőségek (a forráskód importjaiból generálva)"));
  out.push(...bullets([
    "A `service` az egyetlen csomag, amely a `repository`-t és a `rules`-t is használja; a `web` nem lát sem entitást, sem repository-t.",
    "A `web` → `rules` függőség egyetlen segédosztályra szól: a `Periods` az időszakhatárokat (hét, hónap) számolja a statisztikai oldal alapértelmezett időszakához.",
    "A `config` → `service` függőség az `AdminBootstrap`: induláskor a `UserService`-szel hozza létre az első adminisztrátort.",
    "A `dto` nem függ semmitől, ezért bármelyik réteg használhatja; a `domain` csak a `dto` enumjait ismeri.",
  ]));

  out.push(h2("3.4 Telepítési nézet"));
  out.push(...FIG("telepitesi-nezet", 15, "Telepítési nézet"));
  out.push(...bullets([
    "Az alkalmazás **egyetlen Spring Boot JAR**, beágyazott Tomcat-tel. A `Dockerfile` kétlépcsős: JDK-val fordít, majd egy csak JRE-t tartalmazó, nem root felhasználóval futó képbe csomagolja.",
    "A `docker-compose.yml` két szolgáltatást indít: az `app` az alkalmazás, a `db` pedig a **PostgreSQL 16** a `reptrack-db-data` kötettel, állapotellenőrzéssel. Az alkalmazás csak az adatbázis egészséges állapota után indul, így az adatok újraindítás után is megmaradnak.",
    "**HTTPS:** az alkalmazás a 8443-as porton szolgál ki HTTPS-sel. Az önaláírt tanúsítványt a konténer az első induláskor készíti (`docker/entrypoint.sh`, véletlen jelszóval, a `reptrack-certs` kötetbe), így újraindítás után ugyanaz marad. A 8080-as porton csak egy átirányító csatlakozó figyel, amely minden HTTP-kérést HTTPS-re irányít (`HttpsConfig`, `SecurityConfig`). A munkamenet-süti `secure` jelzésű.",
    "Induláskor a **Flyway** végrehajtja a hiányzó migrációkat, majd a Hibernate ellenőrzi, hogy az entitások megfelelnek-e a sémának (`ddl-auto=validate`); eltérésnél az alkalmazás nem indul el.",
    "A konfigurációt a **`.env` fájl** és a környezeti változók adják (10. fejezet). Fejlesztéskor az alkalmazás helyi JDK-val, sima HTTP-n is indítható; ilyenkor a Compose csak az adatbázist futtatja.",
    "A böngésző a Bootstrap, a Chart.js, az ikonok és a betűtípus fájljait **CDN-ről** tölti le; az alkalmazásadatok nem mennek a CDN felé.",
  ]));

  out.push(h2("3.5 Egy kérés útja"));
  out.push(p("A 4. ábra azt mutatja, milyen állomásokon halad át egy kérés, amíg a válasz a böngészőbe ér. Az állomások sorrendje minden oldalnál ugyanaz; az alábbi lista az ábra állomásait magyarázza."));
  out.push(...numbered([
    "**CSRF-szűrő.** Módosító (POST) kérésnél ellenőrzi a tokent; a `th:action` űrlapok automatikusan beleteszik.",
    "**Hitelesítés.** A munkamenetből tölti vissza a felhasználót; ha nincs bejelentkezve, a védett oldalról a `/login` oldalra irányít.",
    "**Jogosultság.** URL-szabályok: nyilvános, USER vagy ADMIN (szerepkör-hierarchiával, 7. fejezet).",
    "**DispatcherServlet és controller.** Útvonal-feloldás, paraméter- és űrlapkötés, `@Valid`; a bejelentkezett felhasználó azonosítóját a controller az `@AuthenticationPrincipal`-ból veszi.",
    "**Szolgáltatás.** Tranzakció, tulajdonos-ellenőrzés, üzleti szabályok; DTO-t ad vissza.",
    "**Repository és PostgreSQL.** Paraméterezett lekérdezések; az adatbázis megszorításai végső védelemként.",
    "**Nézet vagy hibaoldal.** A Thymeleaf a DTO-kból HTML-t készít (minden kimenet escape-elve). Ha a szolgáltatás kivételt dob, a `GlobalExceptionHandler` választ hibaoldalt (404, 400 vagy 409).",
  ], "numbers"));
  out.push(...FIG("kerees-utja", 13.5, "Egy kérés útja a böngészőtől az adatbázisig és vissza", 17.5));

  out.push(h2("3.6 Keresztmetszeti megoldások"));
  out.push(...bullets([
    "**Tranzakciókezelés.** A szolgáltatásosztályok `@Transactional`-ak, a lekérdező metódusok `readOnly = true` módban futnak. A `WorkoutService` létrehozó, módosító és törlő műveletei **egyetlen tranzakcióban** mentik az edzést, számolják újra a PR-eket és értékelik ki a túlterhelési szabályokat, ezért hiba esetén semmi nem marad félig mentve (5.1).",
    "**Validáció három szinten.** (1) Bean Validation a kérés-DTO-kon (`@NotBlank`, `@Min`, `@DecimalMax`, …), a szolgáltatások `@Validated`-ek. (2) Üzleti szabályok a szolgáltatásokban (például az edzés befejezése a kezdése után van). (3) Az adatbázis CHECK, UNIQUE és idegen kulcs megszorításai végső védelemként. A böngészőoldali ellenőrzés csak kényelmi funkció: a szerver mindent újraellenőriz.",
    "**Hibakezelés.** Három szándékos kivételtípus van: `NotFoundException` (404: nem létező vagy más felhasználóé), `BusinessRuleException` (400: üzleti szabály megsértése) és `StaleDataException` (409: egyidejű módosítás). A `GlobalExceptionHandler` mindhármat az egységes hibaoldalra képezi le. Az edzés- és a profilűrlapon az üzleti szabály megsértése a kitöltött űrlap fölött jelenik meg, az adatok megmaradnak; a regisztrációnál a `BindingResult` mezőnkénti üzenetet ad. A sikerről szóló üzenetek átirányítás után is megjelennek (`FlashMessages`).",
    "**Idő és időzóna.** Az időpontok `timestamptz`/`Instant` (UTC) formában tárolódnak. A hét- és hónaphatárok, a napi bontás és a „ma” a `reptrack.timezone` (alapértelmezés: Europe/Budapest) szerint számolódik; a hét hétfőn kezdődik. A pillanatnyi időt a `Clock` bean adja, így a tesztekben rögzíthető.",
    "**Konfiguráció.** Az `application.properties` `${…}` helyőrzőket használ; helyben a projekt gyökerében lévő `.env` fájlt a `DotEnvEnvironmentPostProcessor` olvassa be. A `.env` nem kerül a tárolóba.",
  ]));

  out.push(h2("3.7 Alkalmazott tervezési minták"));
  out.push(tcap("Tervezési minták a rendszerben"));
  out.push(table(["Minta", "Hol", "Mire jó"], [
    ["Rétegelt architektúra", "web / service / repository", "a felelősségek szétválasztása, a rétegek cserélhetősége"],
    ["MVC és Front Controller", "Spring MVC, `DispatcherServlet`, Thymeleaf", "a kérés → controller → nézet menet egységes"],
    ["Repository", "Spring Data JPA repository-k", "az adatelérés elrejtése a szolgáltatások elől"],
    ["Service Layer, Unit of Work", "`*Service`, `@Transactional`", "használati esetenként egy tranzakció"],
    ["DTO, Value Object", "`dto` csomag (Java rekordok)", "az entitás nem szivárog a nézetekbe; változtathatatlan adatok"],
    ["Dependency Injection", "konstruktoros injektálás mindenhol", "laza csatolás, könnyű tesztelhetőség"],
    ["Post/Redirect/Get", "minden módosító kérés átirányít", "az űrlap véletlen újraküldésének elkerülése"],
    ["Template View, Layout", "`fragments/layout.html`", "egységes oldalváz, kevés ismétlődés"],
    ["Tiszta függvények (Domain Logic)", "`rules` csomag", "determinisztikus, adatbázis nélkül tesztelhető számítás"],
    ["Optimista zárolás", "`@Version`, `StaleDataException`", "párhuzamos szerkesztés védelme zárolás nélkül"],
    ["Dinamikus lekérdezés (Specification jellegű)", "`WorkoutRepositoryCustomImpl` (Criteria API)", "opcionális szűrők lapozással"],
    ["Idempotens újraszámolás", "`PersonalRecordService`, `OverloadService`", "a származtatott adat újraépítése minden módosítás után"],
  ], [2600, 3000, 3426], { size: 19 }));

  return out;
};
