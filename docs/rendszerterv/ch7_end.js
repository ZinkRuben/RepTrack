// 7–13. fejezet és függelékek: biztonság, felület, tesztelés, üzemeltetés, korlátok, továbbfejlesztés, összegzés
module.exports = function (c) {
  const { p, h1, h2, bullets, numbered, note, table, tcap, FIG, code } = c;
  const out = [];

  // ------------------------------------------------------------------------------------------------ 7.
  out.push(h1("7. Biztonság"));
  out.push(p("A biztonság keresztmetszeti szempont: a Spring Security minden kérésnél hitelesít és jogosultságot ellenőriz, a szolgáltatások pedig minden műveletnél a tulajdonost is. A következő táblázat a fenyegetésekhez és követelményekhez rendeli a megoldást."));
  out.push(tcap("Biztonsági megoldások"));
  out.push(table(["Terület", "Megoldás"], [
    ["Hitelesítés", "Spring Security űrlapos bejelentkezés e-mail-címmel és jelszóval, saját `/login` oldalon. A felhasználót az `AccountDetailsService` tölti be az adatbázisból; a letiltott fiók (`enabled = false`) nem léphet be."],
    ["Jelszótárolás", "BCrypt hash a `password_hash` oszlopban; a jelszó legalább 8 karakter, legfeljebb 72 bájt (a BCrypt határa). A jelszó sehol nem kerül a naplóba."],
    ["Hibaüzenetek", "Hibás e-mail-címre és hibás jelszóra ugyanaz az üzenet jelenik meg, így nem derül ki, mely címek regisztráltak."],
    ["Munkamenet", "Szerveroldali munkamenet; bejelentkezéskor új munkamenet-azonosító jön létre (session fixation elleni védelem, a Spring Security alapja). A süti HTTPS-en `secure` jelzésű. A kijelentkezés csak POST kéréssel és tokennel működik."],
    ["CSRF", "Bekapcsolva; minden `th:action` űrlap automatikusan megkapja a tokent, a token nélküli módosító kérés elutasításra kerül."],
    ["Jogosultság", "URL-szabályok: a nyitóoldal, a bejelentkezés, a regisztráció, a hibaoldal, a statikus fájlok és a böngészők szokásos háttérkérései (ezekre 404 a válasz) nyilvánosak; az `/admin/**` ADMIN szerepkört kér; minden más USER-t. Szerepkör-hierarchia: ADMIN > USER. A közös gyakorlatokat kezelő szolgáltatásmetódusokat metódusszintű `@PreAuthorize(\"hasRole('ADMIN')\")` is védi (`@EnableMethodSecurity`)."],
    ["Tulajdonos-ellenőrzés", "Minden szolgáltatásművelet a bejelentkezett felhasználó azonosítójával dolgozik; idegen adat „nem található” (404). A testtömeg- és pulzusadatokat csak a tulajdonosuk éri el, az adminisztrátor sem (nincs ilyen funkció)."],
    ["Bemenet-ellenőrzés", "Három szinten: Bean Validation, üzleti szabályok, adatbázis-megszorítások (3.6). Az űrlapmezők típusa és tartománya a böngészőben is korlátozott, de a szerver nem bízik benne."],
    ["SQL injection", "Kizárólag paraméterezett lekérdezések (Spring Data, JPQL, Criteria API); a névkeresés LIKE mintájában a `%`, `_` és `\\` jelek szó szerint értendők."],
    ["XSS", "A Thymeleaf `th:text` minden kimenetet escape-el; `th:utext` sehol nincs használatban."],
    ["Titkok", "Az adatbázis-jelszó és az első adminisztrátor adatai környezeti változóban vagy a tárolón kívüli `.env` fájlban vannak. Az adminisztrátor létrehozása után a jelszó törölhető a `.env`-ből. A tárolóban lévő `.env.example` csak mintaértéket tartalmaz (`CHANGE-ME`); ha ez a jelszó bent marad, az alkalmazás nem indul el, így a minta nem kerülhet élesbe."],
    ["Hibaoldalak", "Egységes hibaoldal; nem jelenít meg veremnyomot vagy technikai részleteket."],
    ["HTTPS", "A Docker-futtatás a 8443-as porton HTTPS-t szolgál ki önaláírt tanúsítvánnyal (első induláskor készül, véletlen jelszóval, kötetben marad). A 8080-as porton érkező HTTP-kérések HTTPS-re irányítódnak. A HSTS fejléc kapcsolható (önaláírt tanúsítványnál alapból ki van kapcsolva). A tanúsítvány és a jelszava nem kerül a tárolóba."],
  ], [2000, 7026], { size: 18 }));
  out.push(p("", { spacing: { after: 60 } }));
  out.push(p("A biztonsági megoldásokat külön automatizált tesztek ellenőrzik (9. fejezet): a tulajdonos-ellenőrzés minden adattípusra, az, hogy minden végpont be van sorolva és védett (új végpont felvételekor a teszt addig bukik, amíg a besorolás hiányzik), a szerepkörök, a regisztráció és a munkamenet-azonosító cseréje bejelentkezéskor."));
  out.push(note("Ismert korlát: az önaláírt tanúsítványt a böngésző első alkalommal figyelmeztetéssel fogadja; éles üzemben hiteles tanúsítvány kell."));

  // ------------------------------------------------------------------------------------------------ 8.
  out.push(h1("8. Felhasználói felület és dizájn"));
  out.push(p("A felület szerveroldali Thymeleaf-sablonokból készül, a megjelenést a Bootstrap 5 és egy saját téma (`static/css/app.css`) adja. A 15. ábra az oldalakat és a köztük lévő navigációt mutatja."));
  out.push(...FIG("oldalterkep", 16, "Az alkalmazás oldaltérképe", 14));
  out.push(tcap("Az oldalak"));
  out.push(table(["Oldal", "Tartalom"], [
    ["Nyitóoldal", "vendégnek bemutatkozás, regisztráció és bejelentkezés; bejelentkezett felhasználónak gyors műveletek"],
    ["Edzésnapló (`/workouts`)", "lapozott lista (20 edzés/oldal), szűrők (időszak, gyakorlat, kategória), nyitott figyelmeztetések"],
    ["Edzés űrlap", "dinamikus gyakorlat- és szettblokkok; a gyakorlathoz csak a releváns mezők (súly, ismétlés, időtartam, távolság, pulzus) látszanak; sablonból indítva előtöltve, tájékoztató sávval"],
    ["Edzés részletei", "szettek, összegzés, az edzésben elért csúcsok, figyelmeztetések; szerkesztés, másolás, mentés sablonként, törlés"],
    ["Sablonok (`/templates`)", "a sablonok gyakorlatonkénti tervvel (pl. „5 × 5 ismétlés · 100 kg”), az archivált gyakorlatok jelölésével; edzés indítása, szerkesztés, törlés"],
    ["Sablon űrlap", "név és gyakorlatsorok; csak a szettek száma kötelező, a többi mező „(nem kötelező)” jelzésű; egy edzésből előtöltve is megnyitható"],
    ["Statisztikák", "időszak- és gyakorlatválasztó, összesítő kártyák, vonal-, oszlop- és kördiagramok, testtömeg-diagram"],
    ["Egyéni csúcsok", "időrendi lista, hivatkozással az edzésre"],
    ["Gyakorlatok", "keresés névre, szűrés kategóriára, izomcsoportra, forrásra és archiváltságra; a saját gyakorlatok kezelése, adminisztrátornak a közösöké is"],
    ["Gyakorlat-űrlap", "új gyakorlat és módosítás: név, kategória, izomcsoportok, rögzíthető mezők; használatban lévő gyakorlatnál a szerkezeti mezők zároltak"],
    ["Profil és célok", "személyes adatok, túlterhelési küszöbök, testtömeg-napló, edzéscélok a haladással"],
  ], [2500, 6526], { size: 19 }));
  out.push(p("", { spacing: { after: 60 } }));
  out.push(...bullets([
    "**Reszponzív.** A Bootstrap rácsrendszere miatt az oldalak telefonon is használhatók; a dinamikus edzésűrlap is egy oszlopra törik, így edzés közben is kitölthető.",
    "**Világos és sötét téma.** Alapból a rendszer (operációs rendszer, böngésző) témáját követi. A navigációs sáv gombja a másik témára vált; a választást a böngésző megjegyzi, és az oldal megjelenítése előtt érvényesül, így nincs villanás. Ha a felhasználó visszavált a rendszer témájára, az oldal ismét a rendszert követi. A böngésző saját vezérlői (például a dátummező ikonja) is a választott témához igazodnak.",
    "**Egységes elemek.** Közös oldalváz, Bootstrap Icons, kártyák, kategória-címkék, magyarázatot adó üres állapotok („Még nincs egyéni csúcsod”).",
    "**Magyar formátum.** A megjelenített számok tizedesvesszővel szerepelnek (82,5 kg), a böngésző nyelvétől függetlenül: az alkalmazás rögzített magyar nyelvi beállítással fut (`spring.web.locale=hu_HU`). A beviteli mezők értéke tizedespontos marad, mert a számmezők azt várják. A dátum- és időpontmezők a flatpickr magyar dátumválasztóját használják (`js/datepicker.js`): a felhasználó „2026. 11. 23. 16:05” alakban látja és gépelheti az értéket, a hét hétfővel kezdődik, az űrlap pedig változatlanul a szabványos alakot küldi. A böngésző saját dátummezőjének formátuma a böngésző nyelvétől függne, ezért cseréltük le; mobilon a készülék saját választója jelenik meg.",
    "**Visszajelzés.** Űrlaphibánál a kitöltött adatok megmaradnak, és az üzenet jelzi a hibát (a regisztrációnál mezőnként); sikerüzenet mentés után; külön hibaoldal a 404, 400 és 409 esetekre.",
    "**Előtöltött űrlapok.** Ha az űrlap sablonból vagy egy edzésből töltődik ki, tájékoztató sáv mondja meg, honnan jönnek az értékek, hogy még semmi nincs mentve, és mely gyakorlatok maradtak ki. A sablonból indított edzésnél a sablonban meg nem adott kötelező mezők halvány kiemelést kapnak, így látszik, mit kell még kitölteni.",
    "**Hozzáférhetőség.** Címkézett űrlapmezők, a dekoratív ikonok `aria-hidden` jelzéssel, a színek mellett szöveges jelölés is van (pl. a nyugtázott állapot felirata).",
    "**Diagramok.** A Chart.js vonal-, oszlop- és kördiagramjai a téma színeihez igazodnak (világos és sötét módban is olvashatók).",
  ]));

  // ------------------------------------------------------------------------------------------------ 9.
  out.push(h1("9. Tesztelés"));
  out.push(p("A tesztstratégia a rétegekhez igazodik. A tiszta szabály-komponenseket adatbázis nélkül, gyorsan teszteljük; minden más (szolgáltatások, adatelérés, séma) **valódi PostgreSQL-lel** fut Testcontainers segítségével. A tesztek egyetlen, közös PostgreSQL 16 konténert használnak (`IntegrationTestBase`), így a Flyway-migrációk, a megszorítások és a lekérdezések a tesztekben is a valós sémán futnak."));
  out.push(tcap("A tesztek összetétele (319 tesztmetódus, a paraméterezett esetekkel 408 futás, 2026. november 23.)"));
  out.push(table(["Réteg", "Mit ellenőriznek", "Eszköz", "Darab"], [
    ["Szabályok (`rules`)", "PR-felismerés, túlterhelési szabályok, statisztikák, képletek (határesetekkel); tiszta egységtesztek", "JUnit 5", "69"],
    ["Szolgáltatások (`service`)", "használati esetek valódi adatbázissal: edzés rögzítése, módosítása, törlése, a PR-ek és figyelmeztetések következményei, szűrés, statisztikák, regisztráció, gyakorlatok keresése és kezelése, edzéssablonok", "Spring Boot Test, Testcontainers", "102"],
    ["Adatelérés és séma (`repository`)", "a CHECK, UNIQUE és idegen kulcs megszorítások tényleges kikényszerítése, a 50 gyakorlatos alapadat épsége, lekérdezések", "Testcontainers", "43"],
    ["Web (`web`)", "ki melyik oldalt éri el (biztonsági szabályok), regisztráció és bejelentkezés, közös oldalváz, gyakorlatböngésző és -kezelés, sablonok oldalai, edzés indítása sablonból és mentés sablonként, HTTP→HTTPS átirányítás", "MockMvc, Spring Security Test", "67"],
    ["Konfiguráció (`config`)", "az első adminisztrátor létrehozása indulásnál, a `.env` beolvasása", "JUnit 5, Spring Boot Test", "12"],
    ["Biztonság (`security`)", "tulajdonos-ellenőrzés (idegen adat 404, az adminisztrátor sem látja), minden végpont besorolása és védelme, szerepkörök, regisztráció és munkamenet, session fixation elleni védelem valódi Tomcat-tal", "MockMvc, valódi szerver", "25"],
    ["Alkalmazás indulása", "a Spring-kontextus hiba nélkül betöltődik", "Spring Boot Test", "1"],
  ], [2300, 4300, 1700, 726], { size: 18 }));
  out.push(p("", { spacing: { after: 60 } }));
  out.push(...bullets([
    "**Futtatás:** `./mvnw verify` (a Docker fusson, mert a tesztek PostgreSQL-konténert indítanak).",
    "**Mit fed le:** a fő folyamatok (edzés mentése → PR és figyelmeztetés), az üzleti szabályok határesetei (24 órás korlát, lezáratlan edzés, azonos eredmény nem csúcs, a nyugtázás megőrzése újraszámoláskor) és a tulajdonos-ellenőrzés (idegen adat 404).",
    "**Felület:** a felhasználói felületet a csapat kézzel próbálja ki; automatizált böngészős (end-to-end) teszt nincs.",
    "**Új oldal felvételekor** az útvonalat a `ProtectedPagesTest` PUBLIC, USER vagy ADMIN listájába kell tenni, különben az `everyEndpointIsClassified` teszt bukik; ezután a vendég-átirányítás és a CSRF-védelem ellenőrzése automatikusan kiterjed rá. Azonosítós felhasználói oldalnál (`GET …/{id}`) a `RoleAccessTest` is kiegészítendő, hogy létező saját adatot nyisson meg.",
  ]));

  // ------------------------------------------------------------------------------------------------ 10.
  out.push(h1("10. Fejlesztői környezet és üzemeltetés"));
  out.push(h2("10.1 Előfeltételek és indítás"));
  out.push(...bullets([
    "JDK 21 (a `JAVA_HOME` ennek a telepítésére mutasson).",
    "Docker (az adatbázishoz és a Testcontainers tesztekhez).",
    "Maven nem kell, a projekt Maven wrappert (`./mvnw`) tartalmaz.",
  ]));
  out.push(...code([
    "docker compose up -d --build   # teljes rendszer: https://localhost:8443",
    "docker compose up -d db        # fejlesztéshez: csak a PostgreSQL (5432)",
    "./mvnw spring-boot:run         # helyi indítás: http://localhost:8080",
    "./mvnw verify                  # tesztek (Docker kell)",
    "./mvnw clean package           # futtatható JAR a target/ mappába",
  ]));
  out.push(p("A telepítés részletes lépéseit a külön **Telepítési dokumentáció** írja le."));

  out.push(h2("10.2 Konfiguráció"));
  out.push(tcap("A konfigurációs beállítások"));
  out.push(table(["Beállítás", "Alapérték", "Jelentés"], [
    ["`DB_URL`", "`jdbc:postgresql://localhost:5432/reptrack`", "az adatbázis JDBC-címe"],
    ["`DB_USER`, `DB_PASSWORD`", "`reptrack`, `reptrack`", "adatbázis-hozzáférés (fejlesztési alapérték; éles üzemben felülírandó)"],
    ["`REPTRACK_ADMIN_EMAIL`, `REPTRACK_ADMIN_PASSWORD`", "(nincs)", "az első adminisztrátor adatai; csak akkor használja, ha még nincs adminisztrátor"],
    ["`REPTRACK_ADMIN_NAME`", "Adminisztrátor", "az első adminisztrátor megjelenített neve"],
    ["`SERVER_PORT`", "8080", "a HTTP-port helyi indításnál (Spring Boot szabványos beállítás)"],
    ["`HTTPS_PORT`, `HTTP_PORT`", "8443, 8080", "a Docker-futtatás gazdagépen kiadott portjai (az átirányítás is ezekre mutat)"],
    ["`REPTRACK_HTTPS_HSTS`", "false", "a HSTS fejléc; valódi domainnél és tanúsítványnál kapcsolható be"],
    ["`SERVER_SSL_KEY_STORE_PASSWORD`", "(a konténer készíti)", "saját tanúsítvány (`reptrack.p12`) esetén annak jelszava"],
    ["`reptrack.timezone`", "Europe/Budapest", "a hét- és hónaphatárok, a napi bontás időzónája"],
  ], [3100, 2900, 3026], { size: 18 }));
  out.push(p("", { spacing: { after: 60 } }));
  out.push(p("Helyben a legegyszerűbb a projekt gyökerében lévő `.env` fájl (a `.env.example` alapján), amely nem kerül a tárolóba. A környezeti változó mindig felülírja a `.env` értékét."));

  out.push(h2("10.3 Csapatmunka és verziókezelés"));
  out.push(...bullets([
    "A munkát a rétegek mentén osztottuk fel; minden területnek egy felelőse van.",
    "A `main` ág védett: a változtatások külön ágon (`feature/<rövid-leírás>`) készülnek, beolvasztás előtt pull request, amelyet egy másik tag átnéz.",
    "A séma változásai új Flyway-migrációként érkeznek; az ER-diagram a migrációkból újragenerálható.",
  ]));

  // ------------------------------------------------------------------------------------------------ 11.
  out.push(h1("11. Megvalósítási állapot és ismert korlátok"));
  out.push(tcap("Hiányzó elemek és ismert korlátok"));
  out.push(table(["Terület", "Állapot vagy korlát", "Terv"], [
    ["Fiókkezelés (UC13)", "az adminisztrátori fiókkezelés (listázás, tiltás, jelszó-visszaállítás) még nem készült el", "fiókok kezelése az `/admin/**` alatt"],
    ["Fiók és adatok törlése (UC14)", "az adatbázis kaszkádolt törlése kész, a felület nincs meg", "megerősítéssel védett törlés"],
    ["Tanúsítvány", "a HTTPS önaláírt tanúsítványt használ, a böngésző első alkalommal figyelmeztet", "hiteles tanúsítvány (pl. Let's Encrypt) valódi domainhez"],
    ["Demófelhasználó", "mintaadatokkal feltöltött felhasználó nincs", "bemutatható, több hónapnyi adat"],
    ["Állóképességi cél", "a haladása nincs definiálva, csak a célt jelenítjük meg", "távolság- vagy időalapú célérték"],
    ["Mértékegységek", "csak metrikus mértékegység van (kg, km)", "mértékegység-választás"],
    ["Felület tesztelése", "nincs automatizált böngészős teszt", "néhány végponttól végpontig teszt a fő folyamatokra"],
  ], [2300, 4500, 2226], { size: 18 }));

  // ------------------------------------------------------------------------------------------------ 12.
  out.push(h1("12. Továbbfejlesztési lehetőségek"));
  out.push(h2("12.1 Funkciók"));
  out.push(...bullets([
    "A **fiókkezelés** (UC13) befejezése; a fiók és az adatok törlése (UC14), valamint **adatexport** (CSV, JSON) a felhasználó kérésére.",
    "**Sablonok szettenkénti tervvel** (például bemelegítő és munkaszettek eltérő súllyal), illetve a sablon frissítése egy újabb edzés alapján.",
    "**További rekordtípusok és célok:** heti volumen, állóképességi célok (távolság, idő), kerékpáros sebesség-PR, pulzuszónák.",
    "**Értesítések:** a túlterhelési figyelmeztetések e-mailben is, ütemezett összefoglaló a heti statisztikáról.",
    "**Edző–tanítvány kapcsolat:** az adatok megosztása egy kiválasztott személlyel, a jelenlegi szigorú adatvédelem megőrzésével (kifejezett hozzájárulás).",
    "**Beállítások:** mértékegység- és nyelvválasztás, magyar számformátum.",
  ]));
  out.push(h2("12.2 Technikai fejlesztések"));
  out.push(...bullets([
    "**Üzemeltetés:** hiteles tanúsítvány és domain, folyamatos integráció (build és tesztek minden pull requestnél), Actuator-alapú állapot- és metrikavégpontok.",
    "**REST API (JSON):** mobilalkalmazás vagy külső kliens számára; a szolgáltatási réteg változtatás nélkül újrahasznosítható, mert a webes réteg csak DTO-kat használ.",
    "**Teljesítmény:** a statisztikák gyorsítótárazása nagy adatmennyiségnél (az újraszámolás az edzésmentésre érvényteleníthető), nagyobb adatkészletekhez az összesítések adatbázis-oldali aggregálása.",
    "**Offline használat:** PWA-támogatás, hogy az edzés a szerver elérése nélkül is rögzíthető legyen (a statikus erőforrások már az alkalmazásba vannak csomagolva).",
    "**Minőségbiztosítás:** végponttól végpontig tesztek a fő folyamatokra, hozzáférhetőségi audit, terheléses mérés.",
  ]));

  // ------------------------------------------------------------------------------------------------ 13.
  out.push(h1("13. Összegzés"));
  out.push(p("A RepTrack egy többrétegű (web, szolgáltatás és szabály-komponens, adatelérés, adatbázis) Spring Boot webalkalmazás, amely az edzések rögzítésére, elemzésére és az egyéni csúcsok, valamint a túlterhelés automatikus felismerésére szolgál. A rendszer magja működik: a regisztráció és bejelentkezés, a profil és a célok, a testtömeg-napló, az edzésnapló dinamikus űrlappal, a statisztikai oldal diagramokkal, az egyéni csúcsok, a túlterhelési figyelmeztetések, az edzéssablonok, valamint a gyakorlatok kezelése (saját és közös)."));
  out.push(p("Az architektúra legfontosabb döntései a következők:"));
  out.push(...bullets([
    "az **egyirányú rétegfüggőség és a DTO-határ**, amely a webes réteget az adatbázistól és a JPA-tól elválasztja;",
    "a **szabályok tiszta függvényekként** a `rules` csomagban, így gyorsan és határeseteikkel együtt tesztelhetők, és minden módosítás után újraszámolhatók;",
    "az **adatbázis mint második védvonal**: a megszorítások akkor is megvédik az adatot, ha egy szoftverrétegben hiba van;",
    "a **tulajdonos-ellenőrzés mint alapelv**: minden művelet a bejelentkezett felhasználó azonosítójával dolgozik;",
    "az **optimista zárolás** és az **egy tranzakcióban végzett mentés és kiértékelés**, amelyek a párhuzamos használatnál is konzisztensen tartják az adatot.",
  ]));
  out.push(p("Néhány funkció még hátra van (fiókkezelés, fióktörlés és a demófelhasználó); ezeket a 11. fejezet sorolja fel. A rétegelt felépítés miatt ezek a meglévő kód módosítása nélkül, új szolgáltatásként és felületként illeszthetők be, a 12. fejezet pedig további irányokat mutat."));

  // ------------------------------------------------------------------------------------------------ függelék
  out.push(h1("Függelék: Képletek és definíciók"));
  out.push(tcap("A rendszerben használt képletek"));
  out.push(table(["Fogalom", "Definíció"], [
    ["Szettvolumen", "súly × ismétlés; saját testsúlyos gyakorlatnál (testtömeg + külső terhelés) × ismétlés. A testtömeg az edzés napján vagy előtte mért legutóbbi érték (ha nincs, csak a külső terhelés számít); kardió szettnek nincs volumene."],
    ["Becsült 1RM", "súly × (1 + ismétlés / 30) (Epley); legfeljebb 10 ismétlésig megbízható, ezért csak addig számolunk vele"],
    ["Relatív erő", "becsült 1RM / testtömeg"],
    ["Átlagtempó", "edzésidő (perc) / távolság (km); a kisebb érték a jobb"],
    ["Átlagsebesség", "távolság (km) / idő (óra)"],
    ["Edzésterhelés", "fáradtságérték (1–10) × edzésidő (perc); csak lezárt edzésre"],
    ["Heti terhelés", "a hét (hétfőtől vasárnapig) lezárt edzéseinek terhelése összesen; az edzés a kezdése szerinti napba tartozik"],
    ["Távsáv", "1 km, 5 km, 10 km, félmaraton (21 097 m), maraton (42 195 m); a legnagyobb sáv, amelynek hossza nem nagyobb a szett távolságánál"],
    ["Célhaladás (testtömeg)", "(aktuális − kezdő) / (cél − kezdő) × 100%, 0 és 100 közé vágva"],
    ["Célhaladás (erő)", "a gyakorlatban elért legnagyobb súly / célsúly × 100%, legfeljebb 100"],
  ], [2300, 6726], { size: 18 }));

  return out;
};
