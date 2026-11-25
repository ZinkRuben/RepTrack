// 4–6. fejezet: a rétegek részletesen, főbb funkciók, adatbázisterv.
// A 6.6 (ER-diagram) fekvő oldalra kerül, ezért a visszatérési érték három részből áll.
module.exports = function (c) {
  const { p, h1, h2, h3, bullets, numbered, note, table, tcap, FIG } = c;
  const before = [];
  const landscape = [];
  const after = [];
  let out = before;

  // ------------------------------------------------------------------------------------------------ 4.
  out.push(h1("4. A rétegek részletesen"));
  out.push(h2("4.1 Webes réteg (`hu.reptrack.web`)"));
  out.push(p("A webes réteg a Spring MVC-re épül. A controllerek fogadják a kérést, az űrlap adatait háttérobjektumhoz kötik (`WorkoutForm`, `TemplateForm`, `ProfileForm`, `RegistrationRequest`), meghívják a szolgáltatást, majd vagy nézetet választanak, vagy átirányítanak. Üzleti döntést nem hoznak, és entitást nem látnak."));
  out.push(tcap("A controllerek, útvonalaik és nézeteik"));
  out.push(table(["Controller", "Útvonalak", "Nézet", "Szolgáltatások"], [
    ["`HomeController`", "GET `/`", "`index`", "–"],
    ["`AuthController`", "GET `/login`; GET, POST `/register`", "`login`, `register`", "`UserService`"],
    ["`WorkoutController`", ["GET `/workouts`, `/workouts/new`, `/workouts/{id}`, `/workouts/{id}/edit`", "POST `/workouts`, `/workouts/{id}`, `/workouts/{id}/delete`, `/workouts/{id}/copy`"], "`workouts`, `workout-form`, `workout`", "`WorkoutService`, `PersonalRecordService`, `OverloadService`, `TemplateService`"],
    ["`TemplateController`", ["GET `/templates`, `/templates/new`, `/templates/{id}/edit`", "POST `/templates`, `/templates/{id}`, `/templates/{id}/delete`"], "`templates`, `template-form`", "`TemplateService`, `WorkoutService`"],
    ["`WarningController`", "POST `/warnings/{id}/acknowledge`", "(átirányít)", "`OverloadService`"],
    ["`RecordController`", "GET `/records`", "`records`", "`PersonalRecordService`"],
    ["`StatsController`", "GET `/stats`", "`stats`", "`StatisticsService`, `PersonalRecordService`"],
    ["`ProfileController`", ["GET, POST `/profile`", "POST `/profile/body-weight`, `/profile/body-weight/{id}/delete`", "POST `/profile/goals`, `/profile/goals/{id}/delete`"], "`profile`", "`ProfileService`, `StatisticsService`"],
    ["`ExerciseController`", ["GET `/exercises`, `/exercises/new`, `/exercises/{id}/edit`", "POST `/exercises`, `/exercises/{id}`, `/exercises/{id}/archive`, `/exercises/{id}/restore`, `/exercises/{id}/delete`"], "`exercises`, `exercise-form`", "`ExerciseService`"],
    ["`AdminExerciseController`", ["GET `/admin/exercises/new`, `/admin/exercises/{id}/edit`", "POST `/admin/exercises`, `/{id}`, `/{id}/archive`, `/{id}/restore`, `/{id}/delete`"], "`exercise-form`", "`ExerciseService`"],
  ], [1700, 3300, 1800, 2226], { size: 18 }));
  out.push(p("", { spacing: { after: 60 } }));
  out.push(...bullets([
    "**Űrlapok és a dinamikus edzésűrlap.** Az edzés rögzítésekor a gyakorlatok és a szettek száma előre nem ismert. Az űrlap indexelt mezőneveket használ (`exercises[i].sets[j].reps`); a böngészőben futó kis szkript blokkonként ad hozzá és töröl sorokat (a gyakorlat típusához igazodó mezőkkel; az új szett az előző szett értékeiből indul, a gyakorlatok sorrendje nyilakkal változtatható), beküldés előtt pedig újraszámozza az indexeket. A szkript csak kényelmi funkció: a szerver a beküldött értékeket teljes egészében újraellenőrzi. A sablon űrlapja ugyanígy működik, gyakorlatonként egy sorral (szettszám és tervezett értékek).",
    "**Sablon és edzés.** Az edzés indítása sablonból (`GET /workouts/new?template={id}`) és a „Mentés sablonként” (`GET /templates/new?fromWorkout={id}`) csak előtölti a megfelelő űrlapot, adatot nem ír; ezért GET kérés, és semmi nem mentődik, amíg a felhasználó a Mentés gombot meg nem nyomja. Az űrlap tetején tájékoztató sáv jelzi, honnan jönnek az értékek, és mely (azóta archivált) gyakorlatok maradtak ki. A másolás ezzel szemben azonnal új edzést ment, ezért POST.",
    "**Post/Redirect/Get és üzenetek.** Minden módosító kérés átirányítással végződik; a visszajelzés flash-attribútumként (`FlashMessages`) jelenik meg az új oldalon, így a frissítés nem küldi el újra az űrlapot.",
    "**Nézetek.** A Thymeleaf sablonok a `fragments/layout.html` közös oldalvázat használják (fej, navigációs sáv, üzenetek, lábléc); az oldalak csak a saját `<title>` és `<main>` részüket adják. Közös darabok: `navbar`, `messages`, `ui` (kategória-címke, üres állapot). A sablonok csak DTO-kat olvasnak.",
    "**Diagramok.** A `StatsController` a szolgáltatás által kiszámolt adatsorokat (címkék, értékek) egyszerű `Map`/`List` szerkezetben adja a modellnek; a sablon JSON-ként beágyazza az oldalba (`th:inline=\"javascript\"`), a Chart.js pedig a böngészőben rajzolja ki őket. A számítás tehát a szerveren történik, a böngésző csak megjelenít.",
    "**Segédosztályok.** `RecordFormatter` (a csúcsok megjelenítési formája, pl. a tempó perc:másodperc alakban), `LayoutModelAdvice` (az aktuális útvonal a menüpont kiemeléséhez), `ExerciseFormSupport` (a gyakorlat-űrlap közös modellje a saját és a közös gyakorlatokhoz), `ExerciseCatalog` (a gyakorlatválasztó adatai az edzés és a sablon űrlapjához), `FormValues` (perc és km átváltása másodpercre és méterre, magyar számformátum), `GlobalExceptionHandler` (hibaoldalak).",
  ]));
  out.push(p("Az oldalak közötti navigációt a 15. ábra mutatja (8. fejezet)."));

  out.push(h2("4.2 Üzleti logikai réteg: szolgáltatások (`hu.reptrack.service`)"));
  out.push(p("A szolgáltatások valósítják meg a használati eseteket. Állapotmentesek (a munkamenet csak a webes réteghez tartozik), tranzakciót kezelnek, validálnak, és DTO-kat adnak vissza."));
  out.push(tcap("A szolgáltatások"));
  out.push(table(["Szolgáltatás", "Felelősség", "Fontosabb műveletek"], [
    ["`WorkoutService`", "az edzésnapló: rögzítés, módosítás, törlés, másolás, szűrt lista, gyakorlatválasztó; a módosítások után elindítja a PR-újraszámolást és a szabálykiértékelést", "`create`, `get`, `update`, `delete`, `copy`, `list`, `exerciseOptions`"],
    ["`TemplateService`", "az edzéssablonok (UC15): mentés, módosítás, törlés, lekérdezés; a gyakorlatokra az edzésével azonos szabályok", "`list`, `get`, `create`, `update`, `delete`"],
    ["`PersonalRecordService`", "az egyéni csúcsok újraszámolása és lekérdezése", "`recalculate`, `list`, `achievedIn`"],
    ["`OverloadService`", "a túlterhelési figyelmeztetések kiértékelése, egyeztetése és nyugtázása", "`evaluate`, `list`, `forWorkout`, `acknowledge`"],
    ["`StatisticsService`", "összesítők, fejlődési görbék, testtömeg-sorozat, célhaladás", "`summaries`, `exerciseProgress`, `bodyWeightSeries`, `goalProgress`, `usedExercises`"],
    ["`ProfileService`", "profil, túlterhelési küszöbök, testtömeg-napló, edzéscélok", "`profile`, `updateProfile`, `saveBodyWeight`, `deleteBodyWeight`, `addGoal`, `deleteGoal`"],
    ["`UserService`", "regisztráció; az első adminisztrátor létrehozása", "`register`, `createInitialAdmin`"],
    ["`AccountDetailsService`", "a Spring Security felhasználó-betöltése e-mail alapján", "`loadUserByUsername`"],
    ["`ExerciseService`", "a gyakorlatok keresése; a saját gyakorlatok kezelése (UC5); a közös gyakorlatok karbantartása adminisztrátornak (UC6)", "`search`, `createOwn`, `updateOwn`, `setOwnArchived`, `deleteOwn`; `createShared`, `updateShared`, `setSharedArchived`, `deleteShared` (`@PreAuthorize`)"],
  ], [2000, 4126, 2900], { size: 18 }));
  out.push(p("", { spacing: { after: 60 } }));
  out.push(p("**Tulajdonos-ellenőrzés.** A szolgáltatásműveletek első paramétere a bejelentkezett felhasználó azonosítója (`userId`). Az entitásokat `findByIdAndUserId` típusú lekérdezéssel töltik be, ezért egy másik felhasználó adata ugyanúgy „nem található” (404), mint egy nem létező azonosító. A válasz így nem árulja el, hogy az azonosító létezik-e (védelem az azonosító-találgatás ellen)."));
  out.push(h3("Az edzés mentésének szabályai"));
  out.push(...bullets([
    "A befejezés a kezdés után van, és az edzés legfeljebb 24 órás.",
    "Lezárt (befejezéssel rendelkező) edzéshez fáradtságérték (1–10) kell.",
    "Csak olyan gyakorlat választható, amely közös vagy a felhasználóé. Archivált gyakorlat új edzésbe nem vehető fel; a régi edzésekben megmarad.",
    "A szett csak a gyakorlathoz beállított mezőket (`tracks_weight`, `tracks_reps`, …) tartalmazhatja. Kötelező az ismétlésszám (ha a gyakorlat követi), a súly (súlyzós gyakorlatnál) és az időtartam (ha követi).",
    "A maximális pulzus nem lehet kisebb az átlagosnál.",
    "Tartományok: súly 0–500 kg, ismétlés 1–100, pulzus 30–230, az időtartam és a távolság pozitív, a cím legfeljebb 120, a megjegyzés legfeljebb 2000 karakter.",
  ]));
  out.push(h3("A profil, a testtömeg és a célok szabályai"));
  out.push(...bullets([
    "A profil módosításakor a megkapott verziószámot is vissza kell küldeni; eltérésnél `StaleDataException` (409). A születési dátum nem lehet jövőbeli, és nem lehet 120 évnél régebbi; a magasság 50–260 cm.",
    "A testtömeg naponta egy értéket vehet fel (az újabb mérés felülírja az aznapit); nem lehet jövőbeli, 20–500 kg közötti. A profil a legutóbbi mérést mutatja, külön nem tárolja.",
    "Cél határideje nem lehet a múltban. A testtömeg-célhoz (fogyás, tömegnövelés) a célsúly 20–500 kg; az erő-célhoz olyan gyakorlat kell, amelyhez súly rögzíthető.",
    "A túlterhelési küszöbök tartománya: heti terhelés aránya 1,05–3,00, izomcsoport-szettek 1–30, fáradtság 1–10.",
  ]));

  out.push(h3("A gyakorlatok kezelésének szabályai (UC5, UC6)"));
  out.push(...bullets([
    "Mindenki a közös és a saját gyakorlatait látja. Más felhasználó privát gyakorlatát senki nem éri el, az adminisztrátor sem: a művelet „nem található” (404) hibát ad, hogy a létezése se derüljön ki.",
    "A saját gyakorlatot csak a tulajdonosa módosíthatja, a közöset csak adminisztrátor. A közös gyakorlatot kezelő szolgáltatásmetódusokat az URL-szabály mellett `@PreAuthorize(\"hasRole('ADMIN')\")` is védi.",
    "A gyakorlat neve egyedi: a közös gyakorlatok között, illetve felhasználónként a saját gyakorlatok között, kis- és nagybetűtől függetlenül (`DuplicateExerciseNameException`).",
    "A **használatban lévő** gyakorlat (edzésben, sablonban vagy célban szerepel) nem törölhető, csak archiválható, és a kategóriája, valamint a rögzíthető mezői nem módosíthatók, hogy a korábbi szettek érvényesek maradjanak. A használaton kívüli gyakorlat törölhető.",
    "Az archivált gyakorlat új edzésbe nem vehető fel, a korábbiakban megmarad, és visszaállítható.",
  ]));

  out.push(h3("Az edzéssablonok szabályai (UC15)"));
  out.push(...bullets([
    "A sablon **terv, nem edzésadat**: nem kerül az edzésnaplóba, és nem számít bele a csúcsokba, a statisztikába és a túlterhelési szabályokba. Törlése a belőle korábban rögzített edzéseket nem érinti.",
    "A név kötelező, legfeljebb 120 karakter, és felhasználónként egyedi, kis- és nagybetűtől függetlenül (`DuplicateTemplateNameException`; az űrlap a névmezőnél jelzi). A sablonban legalább egy gyakorlat van.",
    "Gyakorlatonként csak a **szettek száma** kötelező (1–20). A tervezett ismétlés (1–100), súly (0–500 kg), időtartam és távolság (pozitív) elhagyható, de csak a gyakorlathoz beállított mező adható meg. Az űrlap az elhagyható mezőket „(nem kötelező)” felirattal jelöli.",
    "A gyakorlatokra az edzésével azonos szabályok vonatkoznak: csak közös vagy saját gyakorlat választható (más privát gyakorlata 404), archivált gyakorlat újonnan nem vehető fel, a sablonban már benne lévő megmaradhat.",
    "**Edzés indítása sablonból:** az új edzés űrlapja a sablon nevével, a mostani kezdéssel és gyakorlatonként annyi szettel töltődik ki, ahány a sablonban tervezett, a tervezett értékekkel. Ami a sablonban nincs megadva, az üresen marad, és az edzés szokásos ellenőrzése kéri; az ilyen kötelező mezők halvány kiemelést kapnak, amíg ki nem töltik őket. Az azóta archivált gyakorlatok kimaradnak. Az edzés a mentéskor jön létre, a szokásos kiértékeléssel (5.1).",
    "**Mentés sablonként:** a sablon gyakorlatonként egy tervezett értéksort tárol, ezért a sablon űrlapja úgy töltődik elő, hogy a szettek száma a rögzített szettek száma (legfeljebb 20), a tervezett értékek pedig az **utolsó szett** értékei. A felhasználó mentés előtt bármelyiket átírhatja; az oldal ezt a szabályt ki is írja.",
    "A sablonnak nincs verziószáma (optimista zárolása): csak a tulajdonosa szerkeszti, két egyidejű mentésnél a későbbi érvényes.",
  ]));

  out.push(h2("4.3 Üzleti logikai réteg: szabályok és statisztikák (`hu.reptrack.rules`)"));
  out.push(p("A `rules` csomag a rendszer „számító agya”. Az osztályok **tiszta függvények**: nem ismerik a Spring-et, az adatbázist és az órát, csak a kapott adatokból számolnak. A szolgáltatások töltik be a bemenetet a repository-kból, és mentik a kimenetet."));
  out.push(tcap("A `rules` csomag osztályai"));
  out.push(table(["Osztály", "Feladat"], [
    ["`OneRepMax`", "becsült 1RM Epley-képlettel: súly × (1 + ismétlés / 30); legfeljebb 10 ismétlésig tekintjük megbízhatónak"],
    ["`CardioMetrics`", "átlagtempó (perc/km) és átlagsebesség (km/h)"],
    ["`Periods`", "időszakhatárok: a hét hétfőn kezdődik, a hónap az 1. napon"],
    ["`TrainingLoad`", "edzésterhelés = fáradtságérték × edzésidő percben (csak lezárt edzésre)"],
    ["`StatisticsCalculator`", "heti és havi összesítők, gyakorlatonkénti fejlődési görbe, edzéscél-haladás"],
    ["`PersonalRecordDetector`", "egyéni csúcsok felismerése egy gyakorlat szettjeiből"],
    ["`OverloadDetector`", "a három túlterhelési szabály kiértékelése"],
  ], [2600, 6426], { size: 19 }));

  out.push(h3("Egyéni csúcsok felismerése (UC11, UC16)"));
  out.push(p("A `PersonalRecordDetector` egy gyakorlat **összes** lezárt szettjét időrendben „játssza le”, és közben követi a rekordkulcsonkénti legjobb eredményt. Mivel mindig a teljes előzményből számol, az eredmény szerkesztés, törlés és korábbi dátumra rögzített edzés után is helyes. Az 5. ábra a menetet mutatja."));
  out.push(...FIG("pr-felismeres", 13.5, "Az egyéni csúcsok felismerésének menete", 17));
  out.push(tcap("A rekordtípusok"));
  out.push(table(["Rekordtípus", "Jelentés", "Mely gyakorlatnál"], [
    ["`MAX_WEIGHT_AT_REPS`", "a legnagyobb súly adott ismétlésszámnál", "súlyzós; saját testsúlyosnál a külső terhelés"],
    ["`ESTIMATED_1RM`", "a legnagyobb becsült 1RM", "súlyzós, legfeljebb 10 ismétlésnél"],
    ["`SET_VOLUME`", "a legnagyobb szettvolumen (súly × ismétlés)", "súlyzós"],
    ["`BEST_PACE`", "a legjobb átlagtempó távsávonként", "futás (kardió, `RUNNING` típus)"],
  ], [2400, 3600, 3026], { size: 19 }));
  out.push(p("", { spacing: { after: 60 } }));
  out.push(...bullets([
    "**Csak a szigorúan jobb eredmény csúcs**, az egyenlő nem. Az **első eredmény nem csúcs**, csak alap (nincs mihez hasonlítani).",
    "Csak a **lezárt** edzések számítanak. Egy edzésben egy rekordkulcsra csak a legjobb szett számít (azonos eredménynél a korábbi).",
    "A tempónál a kisebb érték a jobb. A **távsáv**: egy szett a legnagyobb olyan sávba esik, amelynek hossza nem nagyobb a szett távolságánál (1 km, 5 km, 10 km, félmaraton 21 097 m, maraton 42 195 m; így a 6 km-es futás az 5 km-es sávba tartozik, a 900 m-es egyikbe sem).",
    "A csúcsok a `personal_records` táblában tárolódnak, hogy a lista és a diagramok jelölései gyors lekérdezéssel elérhetők legyenek. A tábla **származtatott adat**: a `PersonalRecordService` minden módosítás után törli és újraépíti az érintett gyakorlat csúcsait.",
  ]));

  out.push(h3("Túlterhelési szabályok (UC12, UC16)"));
  out.push(p("A terhelés mérőszáma az **edzésterhelés** (fáradtságérték × edzésidő percben), amely súlyzós és kardióedzésre egyaránt értelmezhető. Az `OverloadDetector` a felhasználó lezárt edzéseiből három szabályt értékel ki; a 6. ábra az egész folyamatot mutatja."));
  out.push(...FIG("tulterheles-kiertekeles", 16, "A túlterhelési szabályok kiértékelése és a figyelmeztetések egyeztetése"));
  out.push(tcap("A túlterhelési szabályok"));
  out.push(table(["Szabály", "Feltétel", "Küszöb (alapérték)"], [
    ["`WEEKLY_LOAD`", "a hét eddigi edzésterhelése meghaladja az előző legfeljebb 4 hét átlagának küszöb-szeresét; legalább 3 előzményhét kell, és csak az első edzés hetétől számolunk, így az induló felhasználó nem kap téves jelzést", "130% (állítható: 105–300%)"],
    ["`MUSCLE_SETS`", "ugyanaz az elsődleges izomcsoport három egymást követő napon, naponta legalább küszöbnyi szettet kap (minden szett számít, a bemelegítő is)", "6 szett (1–30)"],
    ["`HIGH_FATIGUE`", "három egymást követő (lezárt) edzés fáradtságértéke legalább a küszöb", "8 (1–10)"],
  ], [1900, 5326, 1800], { size: 19 }));
  out.push(p("", { spacing: { after: 60 } }));
  out.push(...bullets([
    "Egy találatot a **(szabály, kulcs)** pár azonosít, például `week:2026-11-16`. Az `OverloadService` a találatokat egyezteti a tárolt figyelmeztetésekkel: az újat létrehozza, a meglévőt frissíti (a **nyugtázott állapot megmarad**), a már nem fennállót törli. Így az újraszámolás nem hoz létre duplikátumot.",
    "A 90 napnál régebbi események figyelmeztetései nem keletkeznek, és nem is törlődnek.",
    "A küszöbök a profilban módosíthatók; a módosított érték a következő kiértékeléskor, vagyis a következő edzésmentésnél lép életbe.",
  ]));

  out.push(h3("Statisztikák és célhaladás (UC10)"));
  out.push(...bullets([
    "**Heti és havi összesítő:** az időszakban kezdett edzések száma, a lezárt edzések összideje, összvolumen, megtett távolság, edzésterhelés, a szettek száma kategóriánként.",
    "**Fejlődési görbe gyakorlatonként** (egy pont edzésenként): legnagyobb súly, becsült 1RM, volumen; futásnál tempó, kerékpárnál sebesség; **relatív erő** = becsült 1RM / testtömeg. A diagramon az egyéni csúcsok jelölve vannak.",
    "**Volumen:** súly × ismétlés. Saját testsúlyos gyakorlatnál a súly a testtömeg és a külső terhelés összege; a testtömeg az edzés napján vagy előtte mért legutóbbi érték, ha nincs mérés, csak a külső terhelés számít. Kardió szettnek nincs volumene.",
    "**Edzéscél-haladás:** testtömeg-célnál (fogyás, tömegnövelés) az (aktuális − kezdő) / (cél − kezdő) arány százalékban, 0–100-ra vágva; a kezdő érték a cél létrehozásának napján érvényes mérés. Erő-célnál a gyakorlatban elért legnagyobb súly és a célsúly aránya. Állóképességi célnál a haladás nincs definiálva, ott csak a célt jelenítjük meg.",
  ]));

  out.push(h3("Az edzés életciklusa"));
  out.push(p("Az edzés két állapotban lehet. A **nyitott** edzés még folyamatban van (nincs befejezése), a **lezárt** edzésnek van befejezése és fáradtságértéke. A származtatott funkciók (csúcsok, szabályok, időtartam-statisztikák) csak a lezárt edzéseket használják. A 7. ábra az állapotokat és az átmeneteket mutatja; az adatbázis a `ck_workouts_closed` megszorítással a lezárt állapot szabályait is kikényszeríti."));
  out.push(...FIG("edzes-eletciklus", 14.5, "Az edzés állapotai"));
  out.push(note("A nyitott edzés nem számít bele az egyéni csúcsokba és a túlterhelési szabályokba. Ezért az edzés rögzítésekor a „Befejezés” mezőt is ki kell tölteni (és a fáradtságot), különben az edzés nyitott marad."));

  out.push(h2("4.4 Adatelérési réteg (`hu.reptrack.repository`)"));
  out.push(p("Az adatelérést Spring Data JPA repository-k végzik. Az egyszerű lekérdezések névből képzett (derived) metódusok, az összetettebbek JPQL (`@Query`), a dinamikus szűrés pedig a Criteria API segítségével készül."));
  out.push(tcap("A repository-k és jellemző lekérdezéseik"));
  out.push(table(["Repository", "Jellemző lekérdezések"], [
    ["`UserRepository`", "`findByEmailIgnoreCase`, `existsByEmailIgnoreCase`, `existsByRole`"],
    ["`ExerciseRepository`", "gyakorlatkeresés névre (a LIKE speciális karakterei szó szerint értendők), kategóriára, izomcsoportra, forrásra és archiváltságra; névegyediség-ellenőrzések (`existsBy…NameIgnoreCase`); `findIdsUsedInWorkouts`, `…InTemplates`, `…InGoals`: mely gyakorlatok vannak használatban"],
    ["`ExerciseOptionRepository`", "`findAvailable`: a felhasználó számára választható (közös és saját) gyakorlatok DTO-ként"],
    ["`WorkoutRepository` és `WorkoutRepositoryCustomImpl`", "`findByIdAndUserId`; `search`: dinamikus szűrés (időszak, gyakorlat, kategória) és lapozás Criteria API-val, stabil sorrenddel"],
    ["`StatisticsRepository`", "`findWorkouts`, `findSets`, `findBodyWeights`, `findRecordSets`, `findMuscleSetCounts`, `findUsedExercises`: `select new …` JPQL-lekérdezések, amelyek közvetlenül DTO-t adnak"],
    ["`PersonalRecordRepository`", "`deleteByUserAndExercise`; nézet-lekérdezések felhasználóra, gyakorlatra és edzésre"],
    ["`OverloadWarningRepository`", "`findByUserId`, `findOpenViewsByUser`, `findViewsByWorkout`, `findByIdAndUserId`"],
    ["`WorkoutTemplateRepository`", "`findByUserIdOrderByName`, `findByIdAndUserId` (a sablon gyakorlataival együtt, egy lekérdezéssel: `@EntityGraph`), névegyediség-ellenőrzések (`existsByUserIdAndNameIgnoreCase…`)"],
    ["`BodyWeightEntryRepository`, `GoalRepository`, …", "felhasználóra szűrt listák és `findByIdAndUserId`"],
  ], [3200, 5826], { size: 18 }));
  out.push(p("", { spacing: { after: 60 } }));
  out.push(...bullets([
    "**Paraméterezett lekérdezések.** Sehol nincs SQL- vagy JPQL-szöveg összefűzés felhasználói bemenettel, így az SQL injection ki van zárva (7. fejezet).",
    "**DTO-vetítés.** A statisztikai és nézet-lekérdezések közvetlenül rekordokat adnak, nem entitásokat, ezért kevesebb adatot töltenek be, és nem kell lusta betöltés.",
    "**Tulajdonos szerinti szűrés.** A `findByIdAndUserId` minta minden felhasználói adatnál megvan (4.2).",
    "**Leválasztott származtatott entitások.** A mentett PR-eket és figyelmeztetéseket a szolgáltatás a mentés után leválasztja (`detach`), hogy egy későbbi szett- vagy edzéstörlés ne okozzon hivatkozási hibát; az adatok úgyis újraszámolódnak.",
  ]));

  out.push(h2("4.5 Domain és közös definíciók (`domain`, `dto`)"));
  out.push(p("A `domain` csomag 11 JPA entitást tartalmaz, amelyek a 6. fejezetben leírt táblákra képződnek le. A `dto` csomag a rétegek közös nyelve."));
  out.push(tcap("A DTO-k csoportjai"));
  out.push(table(["Csoport", "Példák", "Szerep"], [
    ["Kérés-objektumok", "`WorkoutRequest`, `TemplateRequest`, `ProfileRequest`, `BodyWeightRequest`, `GoalRequest`, `ExerciseRequest`, `RegistrationRequest`", "a bemenet; Bean Validation szabályokkal"],
    ["Nézet-objektumok", "`WorkoutView`, `WorkoutSummary`, `TemplateView`, `ProfileView`, `PersonalRecordView`, `OverloadWarningView`, `ExerciseView`, `EditableExercise`", "a szolgáltatások kimenete a nézeteknek"],
    ["Számítási rekordok", "`WorkoutRow`, `SetRecord`, `RecordSet`, `MuscleSetCount`, `PeriodSummary`, `ExerciseProgressPoint`, `GoalProgress`", "a `rules` csomag be- és kimenete"],
    ["Enumok", "`ExerciseCategory`, `MuscleGroup`, `CardioType`, `GoalType`, `RecordType`, `OverloadRule`, `DistanceBand`, `FitnessLevel`, `Role`, `Granularity`, `ExerciseSource`", "zárt értékkészletek; az adatbázisban CHECK védi őket"],
    ["Kivételek", "`NotFoundException`, `BusinessRuleException`, `StaleDataException`, `DuplicateEmailException`, `DuplicateExerciseNameException`, `DuplicateTemplateNameException`", "hibajelzés a rétegek között"],
  ], [1900, 4526, 2600], { size: 18 }));

  out.push(h2("4.6 Konfiguráció és indulás (`hu.reptrack.config`)"));
  out.push(...bullets([
    "**`SecurityConfig`**: a szűrőlánc, az URL-szabályok, a szerepkör-hierarchia (ADMIN > USER), a BCrypt kódoló, HTTPS esetén a HTTP→HTTPS átirányítás és a HSTS-beállítás (7. fejezet).",
    "**`HttpsConfig`**: HTTPS esetén (docker profil) egy második, sima HTTP-csatlakozót nyit, amely csak az átirányításra való.",
    "**`ClockConfig`**: a `Clock` bean, amelyet az időfüggő szolgáltatások használnak.",
    "**`DotEnvEnvironmentPostProcessor`**: a `.env` fájl beolvasása a Spring környezetébe (a `META-INF/spring.factories` regisztrálja).",
    "**`AdminBootstrap`**: indulás után létrehozza az első adminisztrátort a `REPTRACK_ADMIN_*` változókból, ha még nincs adminisztrátor. Ha már van, nem történik semmi (a jelszót sem írja át); hibás adatnál az alkalmazás nem indul el; a jelszó nem kerül a naplóba.",
    "**Flyway**: induláskor lefuttatja a hiányzó migrációkat, mielőtt a Hibernate ellenőrizné a sémát.",
  ]));

  // ------------------------------------------------------------------------------------------------ 5.
  out.push(h1("5. Főbb funkciók működése"));
  out.push(p("Ez a fejezet szekvenciadiagramokon mutatja be a legfontosabb folyamatokat. A számok a diagram lépéseinek sorszámai."));

  out.push(h2("5.1 Edzés rögzítése és kiértékelése (UC7, UC16)"));
  out.push(p("A 8. ábra egy új edzés mentését mutatja; a lépések alább a diagram sorszámaira hivatkoznak."));
  out.push(...bullets([
    "**1–3.** A böngésző elküldi az űrlapot a CSRF-tokennel. A controller a `WorkoutForm`-ot `WorkoutRequest`-té alakítja: a helyi időpontokat a beállított időzóna szerint `Instant`-tá váltja.",
    "**4.** A szolgáltatás validálja a kérést (Bean Validation) és az üzleti szabályokat: időtartam, a gyakorlat láthatósága, a szettmezők (4.2).",
    "**5.** Az edzés mentése (`saveAndFlush`), hogy az azonosítók már létezzenek a következő lépésekhez.",
    "**6–9.** A `PersonalRecordService` minden érintett gyakorlatra betölti a lezárt szetteket, lefuttatja a `PersonalRecordDetector`-t, törli a gyakorlat régi csúcsait és menti az újakat.",
    "**10–13.** Az `OverloadService` betölti a lezárt edzéseket és az izomcsoportonkénti szettszámokat, lefuttatja az `OverloadDetector`-t, és egyezteti az eredményt a tárolt figyelmeztetésekkel.",
    "**5–13. egy tranzakció.** A `WorkoutService.create` tranzakciójához a két másik szolgáltatás csatlakozik, ezért bármelyik lépés hibája az egész mentést visszagörgeti.",
    "**14–15.** A szolgáltatás nézet-DTO-t ad vissza, a controller átirányít az edzés oldalára, sikerüzenettel (Post/Redirect/Get). Az oldal betöltésekor a `PersonalRecordService.achievedIn` és az `OverloadService.forWorkout` adja az edzésben elért csúcsokat és a hozzá tartozó figyelmeztetéseket.",
    "**Módosítás és törlés.** Ugyanígy működnek. Az újraszámolás az **érintett gyakorlatok** egyesítésére fut: a módosítás előtti és utáni gyakorlatokra, mert egy gyakorlat kivétele az edzésből is változtathatja a csúcsokat.",
  ]));
  out.push(...FIG("szekvencia-edzes-mentese", 14.5, "Új edzés mentése: a PR-ek és a figyelmeztetések kiértékelése", 22));

  out.push(h2("5.2 Regisztráció és bejelentkezés (UC1)"));
  out.push(p("A bejelentkezés lépéseit a 9. ábra mutatja."));
  out.push(...bullets([
    "**Regisztráció.** Az `AuthController` a `RegistrationRequest`-et validálja (név, e-mail-formátum, jelszó legalább 8 karakter, a két jelszó egyezik, legfeljebb 72 bájt). A `UserService` az e-mail-címet kisbetűsre alakítja, kis- és nagybetűtől függetlenül ellenőrzi az egyediséget (`DuplicateEmailException`), a jelszót BCrypt-hash-ként menti, a szerepkör USER.",
    "**Bejelentkezés.** A szűrőlánc a `POST /login` kérést az `AccountDetailsService`-szel dolgozza fel: e-mail alapján betölti a felhasználót (az `AuthenticatedUser` hordozza az azonosítót, a szerepkört és a hash-t), a BCrypt összeveti a jelszót, siker esetén új munkamenet indul.",
    "**Hiba.** Hibás e-mailre és hibás jelszóra ugyanaz az üzenet jelenik meg, a letiltott fiók külön üzenetet kap. A védett oldalra érkező, be nem jelentkezett kérés a `/login` oldalra irányít.",
  ]));
  out.push(...FIG("szekvencia-bejelentkezes", 13.5, "Bejelentkezés a Spring Security szűrőláncával"));

  out.push(h2("5.3 Statisztikák megtekintése (UC10)"));
  out.push(p("Az oldal összeállítását a 10. ábra mutatja."));
  out.push(...bullets([
    "A `StatsController` az időszakot (alapértelmezés: az utolsó 12 hét vagy hónap) és a bontást (hét/hónap) a kérés paramétereiből állítja össze; hibás időszaknál (a kezdet a vég után van) figyelmeztet, és az alapértelmezett időszakot mutatja.",
    "A `StatisticsService` a repository-ból tölti a saját edzéseket, szetteket és testtömeg-méréseket (mindig a bejelentkezett felhasználóra szűrve), a `StatisticsCalculator` pedig összesíti őket.",
    "A kiválasztott gyakorlat fejlődési görbéje, a testtömeg-sorozat és a PR-jelölések külön hívásokkal készülnek. A controller a számokat egyszerű listákba rendezi, és JSON-ként beágyazza az oldalba; a diagramokat a böngészőben a Chart.js rajzolja ki.",
  ]));
  out.push(...FIG("szekvencia-statisztika", 14.5, "A statisztikai oldal összeállítása"));

  out.push(h2("5.4 Egyidejű módosítás kezelése"));
  out.push(p("Ha ugyanazt az edzést két eszközön egyszerre szerkesztik, a később mentő nem írhatja felül észrevétlenül a másik módosítását. Az edzés, a profil és a gyakorlat `version` mezőt tartalmaz (optimista zárolás)."));
  out.push(...bullets([
    "Az űrlap rejtett mezőben hordozza a betöltéskor látott verziószámot. Mentéskor a szolgáltatás összeveti a tárolt verzióval; eltérésnél `StaleDataException` keletkezik, amelyből a `GlobalExceptionHandler` 409-es hibaoldalt készít („Az edzést közben máshol módosították…”). A felhasználó újratölti az oldalt, és újra elvégzi a módosítást.",
    "Második védelmi vonalként a JPA `@Version` mező az `UPDATE … WHERE version = ?` feltétellel az adatbázis szintjén is megakadályozza a felülírást.",
  ]));
  out.push(...FIG("szekvencia-konkurens-modositas", 12.5, "Két eszköz egyidejű szerkesztése: a második mentés elutasítása"));

  out.push(h2("5.5 Figyelmeztetések nyugtázása (UC12)"));
  out.push(p("A nyitott figyelmeztetések az edzésnapló tetején és az érintett edzés oldalán jelennek meg. A „Nyugtázom” gomb a `POST /warnings/{id}/acknowledge` kérést küldi; az `OverloadService.acknowledge` a figyelmeztetést tulajdonos szerint tölti be (idegen figyelmeztetés 404), és nyugtázottra állítja. A nyugtázott állapot az újraszámolások után is megmarad (4.3)."));

  // ------------------------------------------------------------------------------------------------ 6.
  out.push(h1("6. Adatbázisterv"));
  out.push(h2("6.1 Áttekintés"));
  out.push(p("Az adatbázis **PostgreSQL 16**. A séma kizárólag a Flyway-migrációkból (`src/main/resources/db/migration`) épül fel, a Hibernate pedig csak ellenőrzi (`ddl-auto=validate`). A 12. ábra a táblák közötti kapcsolatokat mutatja áttekintő szinten; a színek a táblák csoportjait jelölik (kék: felhasználó, zöld: gyakorlat, narancs: edzésnapló, lila: sablonok, piros: származtatott adatok). Az oszlopszintű ER-diagram a fejezet végén, a 6.6 szakasz fekvő oldalain van."));
  out.push(...FIG("adatmodell-attekintes", 16, "Az adatmodell áttekintése (nyilak: egy-a-többhöz kapcsolat, a nyílhegy a „sok” oldalon)"));

  out.push(h2("6.2 A táblák leírása"));
  out.push(tcap("A táblák és szerepük"));
  out.push(table(["Tábla", "Tartalom", "Megjegyzés"], [
    ["`users`", "felhasználó: e-mail, jelszó-hash, név, születési dátum, magasság, edzettségi szint, szerepkör, engedélyezettség, túlterhelési küszöbök, `version`", "az e-mail kis- és nagybetűtől függetlenül egyedi; a testtömeg nincs itt"],
    ["`body_weight_entries`", "testtömeg-napló: nap és súly", "naponta egy mérés; 20–500 kg"],
    ["`exercises`", "gyakorlat: név, kategória, elsődleges izomcsoport, kardió típus, leírás, `tracks_*` jelzők, archivált, `version`", "`owner_id` üres = közös, kitöltött = privát"],
    ["`exercise_secondary_muscles`", "a gyakorlat másodlagos izomcsoportjai", "(gyakorlat, izomcsoport) összetett kulcs"],
    ["`goals`", "edzéscél: típus, opcionális gyakorlat, célérték, határidő, aktív", "a haladás nem tárolt, számolt"],
    ["`workouts`", "edzés: cím, kezdés, befejezés, fáradtság, megjegyzés, `version`", "`ck_workouts_closed`"],
    ["`workout_exercises`", "az edzés gyakorlatai sorrenddel és megjegyzéssel", ""],
    ["`workout_sets`", "szett: súly, ismétlés, időtartam, távolság, átlag- és maximális pulzus", "CHECK tartományok"],
    ["`workout_templates`", "edzéssablon: név", "a név felhasználónként egyedi (kis- és nagybetűtől függetlenül)"],
    ["`template_exercises`", "a sablon gyakorlatai sorrenddel: tervezett szettszám, ismétlés, súly, időtartam, távolság", "gyakorlatonként egy tervezett értéksor; CHECK tartományok"],
    ["`personal_records`", "felismert csúcs: típus, ismétlés, távsáv, érték, a rekordot elérő szett, dátum", "származtatott, újraszámolható; `ck_personal_records_shape`"],
    ["`overload_warnings`", "figyelmeztetés: szabály, kulcs, esemény dátuma, üzenet, nyugtázva", "egyedi: (felhasználó, szabály, kulcs)"],
  ], [2200, 4300, 2526], { size: 18 }));

  out.push(h2("6.3 Megszorítások és indexek"));
  out.push(...bullets([
    "**Tartományok (CHECK):** magasság 50–260 cm, testtömeg 20–500 kg, fáradtság 1–10, súly 0–500 kg, ismétlés 1–100, pulzus 30–230, az időtartam és a távolság pozitív; a sablonban a tervezett szettszám 1–20, a tervezett ismétlés és súly ugyanazokkal a határokkal, mint a szettnél.",
    "**Enum-értékek (CHECK):** szerepkör, edzettségi szint, kategória, izomcsoport, kardió típus, célok típusa, rekordtípus, távsáv, szabálykód.",
    "**`ck_exercises_cardio_type`:** a kardió típus pontosan a kardió kategóriájú gyakorlatnál van megadva (futás: tempó, kerékpár: sebesség).",
    "**`ck_workouts_closed`:** a befejezett edzéshez fáradtság és a kezdés utáni befejezés tartozik.",
    "**`ck_personal_records_shape`:** a rekordtípushoz kötelező vagy tiltott mezők (a tempó-PR-hez távsáv kell és ismétlés nem; a súly-PR-hez ismétlés kell és távsáv nem).",
    "**Egyediség:** `users.email`; (`user_id`, `measured_on`) a testtömegnél; a gyakorlatnév kis- és nagybetűtől függetlenül egyedi a közös gyakorlatok között és felhasználónként a privátoknál (részleges egyedi indexek); a sablon neve felhasználónként, kis- és nagybetűtől függetlenül (`ux_workout_templates_name`); (`user_id`, `rule_code`, `rule_key`) a figyelmeztetéseknél.",
    "**Idegen kulcsok `ON DELETE CASCADE`-del:** a felhasználó törlése minden adatát viszi (az UC14 alapja), az edzés törlése a szettjeit, csúcsait és figyelmeztetéseit.",
    "**Indexek:** `idx_workouts_user_started` (a napló és a statisztikák fő szűrője), `idx_workout_exercises_workout` és `_exercise`, `idx_workout_sets_we`, `idx_template_exercises_template` és `_exercise`, `idx_pr_user_exercise`, `idx_warnings_user`, `idx_exercises_owner` és `_category`.",
  ]));

  out.push(h2("6.4 Tervezési döntések"));
  out.push(...bullets([
    "**A testtömeg csak a naplóban él.** Nincs külön „aktuális testtömeg” mező, a profil a legutóbbi mérést mutatja; így nem lehet ellentmondás.",
    "**A számított értékek nem tárolódnak.** Az 1RM, a volumen, a tempó, a terhelés és a célhaladás lekérdezéskor számolódik. Kivétel a PR és a figyelmeztetés: ezek időrendi lejátszást igényelnek, és gyakran jelennek meg listában, ezért tároljuk őket, de minden módosítás után újraszámoljuk.",
    "**Közös és privát gyakorlat egy táblában.** Az `owner_id` NULL vagy kitöltött. Az edzésnaplóban szereplő gyakorlat nem törölhető, csak archiválható (`archived`), így a régi edzések érintetlenek maradnak.",
    "**Időpontok és napok.** Az időpontok `timestamptz` típusúak (UTC); a naphoz kötött adatok (mérés, cél határideje, csúcs dátuma) `date` típusúak.",
    "**Optimista zárolás.** A gyakran szerkesztett táblák (`users`, `exercises`, `workouts`) `version` oszlopot kapnak. A sablon nem: csak a tulajdonosa szerkeszti.",
    "**A sablon gyakorlatonként egy tervezett értéksort tárol** („3 × 8 × 80 kg”), nem szettenként. A terv így egyszerű; edzés indításakor a sor a tervezett számú, azonos értékű szettre bomlik, amelyet a felhasználó az edzés közben igazíthat.",
    "**A `rule_key` az idempotens egyeztetéshez kell:** a figyelmeztetés a szabály konkrét előfordulását azonosítja, így az újraszámolás nem duplikál, és a nyugtázás megmarad.",
  ]));

  out.push(h2("6.5 Verziózás és kezdőadatok"));
  out.push(tcap("A Flyway-migrációk"));
  out.push(table(["Migráció", "Tartalom"], [
    ["`V1__init.sql`", "az alapséma: táblák, kulcsok, megszorítások, indexek"],
    ["`V2__seed_exercises.sql`", "az alapadatbázis: 50 közös gyakorlat másodlagos izomcsoportokkal"],
    ["`V3__personal_record_checks.sql`", "a csúcsok távsáv- és alakmegszorításai"],
    ["`V4__overload_warning_keys.sql`", "`rule_key` és `event_date` a figyelmeztetésekhez, egyedi index"],
    ["`V5__template_constraints.sql`", "a sablonok tervezett értékeinek CHECK megszorításai, a sablonnév egyedisége felhasználónként, indexek"],
  ], [3300, 5726], { size: 19 }));
  out.push(p("", { spacing: { after: 60 } }));
  out.push(p("Az új migráció új fájl (a már lefuttatott migrációkat nem módosítjuk). Az ER-diagram újragenerálása: `python3 docs/generate-er.py` (egy ábra), illetve `python3 docs/rendszerterv/diagrams.py` (a jelen dokumentum ábrái)."));

  // ---- fekvő rész ----
  out = landscape;
  out.push(h2("6.6 ER-diagram"));
  out.push(p("A diagram a Flyway-migrációkból készül (`docs/rendszerterv/diagrams.py`; a teljes, egy ábrás változat a `docs/er-diagram.png`). Jelmagyarázat: PK = elsődleges kulcs, FK = idegen kulcs, UQ = egyedi; a `?` a típus mögött nullázható oszlopot jelöl. A kapcsolat „tee” vége az „egy”, a „varjúláb” vége a „sok” oldal.", { spacing: { after: 60 } }));
  out.push(...FIG("er-1-felhasznalo-gyakorlat", 24, "ER-diagram 1/2: felhasználó, célok, gyakorlatok, sablonok", 13.2));
  out.push(...FIG("er-2-edzesnaplo", 25.5, "ER-diagram 2/2: edzésnapló, egyéni csúcsok, figyelmeztetések (a users és az exercises tábla az előző ábrán részletes)", 13.2));


  return { before, landscape, after };
};
