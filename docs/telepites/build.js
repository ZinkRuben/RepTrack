// A Telepítési dokumentáció előállítása: node build.js <kimenet.docx>
// Előfeltétel: `docx` npm modul (NODE_PATH vagy helyi telepítés). A stílusokat a ../rendszerterv/lib.js adja.
const fs = require("fs");
const {
  Document, Packer, Paragraph, TextRun, ShadingType, BorderStyle, AlignmentType, LevelFormat, Footer, PageNumber,
  TabStopType,
} = require("docx");
const L = require("../rendszerterv/lib");
const { p, h1, h2, bullets, numbered, note, table, tcap } = L;

const out = process.argv[2] || "Edzeskoveto_Telepitesi_Dokumentacio.docx";

function code(lines) {
  return lines.map((t, i) => new Paragraph({
    children: [new TextRun({ text: t, font: L.MONO, size: 19 })],
    keepNext: i < lines.length - 1,
    keepLines: true,
    spacing: { before: i === 0 ? 60 : 0, after: i === lines.length - 1 ? 160 : 0, line: 252 },
    indent: { left: 200, right: 100 },
    shading: { type: ShadingType.CLEAR, fill: "F2F2F2", color: "auto" },
  }));
}

const c = [];

// ---- fejléc ----
c.push(new Paragraph({
  spacing: { after: 60 },
  children: [new TextRun({ text: "TELEPÍTÉSI DOKUMENTÁCIÓ", bold: true, size: 26, color: L.BLUE2, characterSpacing: 60 })],
}));
c.push(new Paragraph({
  spacing: { after: 80 },
  children: [new TextRun({ text: "Edzéskövető és elemző alkalmazás (RepTrack)", bold: true, size: 44, color: L.BLUE })],
}));
c.push(new Paragraph({
  spacing: { after: 280 },
  border: { bottom: { style: BorderStyle.SINGLE, size: 12, color: L.BLUE2, space: 8 } },
  children: [new TextRun({ text: "Szoftverarchitektúrák (VIAUMA21) · 2026. őszi félév · Dokumentumverzió: 1.0", size: 22, color: "404040" })],
}));

// ---- 1. ----
c.push(h1("1. Áttekintés"));
c.push(p("A RepTrack egy Java 21-es, Spring Boot alapú webalkalmazás, amely PostgreSQL adatbázist használ. Két módon telepíthető:"));
c.push(...bullets([
  "**Dockerrel (ajánlott).** Egyetlen paranccsal elindul az alkalmazás és az adatbázis is, az alkalmazás HTTPS-en érhető el. Ehhez csak a Dockerre van szükség, Java vagy PostgreSQL telepítése nem kell.",
  "**Docker nélkül.** A futtatható JAR-t Java 21-gyel indítjuk, a PostgreSQL-t külön kell biztosítani.",
]));

// ---- 2. ----
c.push(h1("2. Követelmények"));
c.push(tcap("Követelmények"));
c.push(table(["", "Docker-es telepítés", "Docker nélküli telepítés"], [
  ["Szoftver", "Docker Engine és a Compose (`docker compose` bővítmény vagy az önálló `docker-compose`)", "JDK vagy JRE 21; PostgreSQL 16"],
  ["Erőforrás", "kb. 2 GB szabad memória, 2 GB szabad tárhely (képek, Maven-gyorsítótár)", "kb. 1 GB szabad memória"],
  ["Hálózat", "internet az első építéshez (a Maven-függőségek és az alapképek letöltése)", "–"],
  ["Portok", "8443 (HTTPS), 8080 (átirányítás), 5432 (PostgreSQL) szabad legyen", "8080 szabad legyen"],
  ["Böngésző", "modern böngésző (Chrome, Firefox, Edge, Safari); internet-hozzáférés, mert a Bootstrap, a Chart.js és a betűtípus CDN-ről töltődik", "ugyanaz"],
], [1400, 4300, 3326], { size: 19, boldFirst: true }));

// ---- 3. ----
c.push(h1("3. Telepítés Dockerrel (ajánlott)"));
c.push(...numbered([
  "Csomagold ki a forráscsomagot, és lépj a projekt mappájába: `cd RepTrack`.",
  "Hozd létre a beállítófájlt a mintából: `cp .env.example .env`. Nyisd meg, és add meg az első adminisztrátor adatait: `REPTRACK_ADMIN_EMAIL`, `REPTRACK_ADMIN_PASSWORD` (legalább 8 karakter) és `REPTRACK_ADMIN_NAME`. **A minta jelszavát mindenképpen cseréld le egy sajátra.**",
  "Építsd meg és indítsd el a rendszert (az első építés néhány percig tart; a tesztek a képépítéskor nem futnak le):",
], "numbers"));
c.push(...code([
  "docker compose up -d --build",
]));
c.push(note("Ha a gépen nincs `docker compose` parancs (a Compose bővítmény hiányzik), használd az önálló változatot: `docker-compose up -d --build`. Minden további `docker compose` parancs ugyanígy cserélhető."));
c.push(...numbered([
  "Ellenőrizd, hogy minden elindult: `docker compose ps`. Mindkét szolgáltatás (`db` és `app`) legyen „healthy”. A naplót a `docker compose logs -f app` mutatja; sikeres induláskor megjelenik az „Adminisztrátori fiók létrehozva” sor.",
  "Nyisd meg a böngészőben a **https://localhost:8443** címet. A tanúsítvány önaláírt, ezért a böngésző első alkalommal figyelmeztet: a „Speciális” gombbal folytasd a megnyitást. A `http://localhost:8080` cím automatikusan a HTTPS-címre irányít.",
  "Jelentkezz be az 1. lépésben megadott adminisztrátori e-mail-címmel és jelszóval, vagy a „Regisztráció” oldalon hozz létre új felhasználót.",
], "n2"));

c.push(h2("3.1 Leállítás, újraindítás, törlés"));
c.push(...code([
  "docker compose stop           # leállítás (az adatok megmaradnak)",
  "docker compose start          # újraindítás",
  "docker compose down           # konténerek törlése (az adatok megmaradnak)",
  "docker compose down -v        # mindent töröl (adatbázis, tanúsítvány is)",
]));
c.push(h2("3.2 Más portok"));
c.push(p("Ha a 8443-as vagy a 8080-as port foglalt, add meg a `.env` fájlban: `HTTPS_PORT=9443` és `HTTP_PORT=9080`. Az átirányítás is ezekre a portokra mutat."));

// ---- 4. ----
c.push(h1("4. Telepítés Docker nélkül (futtatható JAR)"));
c.push(...numbered([
  "Biztosíts egy PostgreSQL 16 adatbázist `reptrack` néven, `reptrack` felhasználóval. Ha van Docker, a mellékelt Compose fájl csak az adatbázist is el tudja indítani: `docker compose up -d db` (hozzáférés: `reptrack` / `reptrack`, a 5432-es porton).",
  "Állítsd be a környezeti változókat (vagy hozz létre `.env` fájlt a JAR mellett): `DB_URL`, `DB_USER`, `DB_PASSWORD`, valamint az adminisztrátorhoz `REPTRACK_ADMIN_EMAIL`, `REPTRACK_ADMIN_PASSWORD`. Az alapértékek a helyi adatbázishoz illenek.",
  "Indítsd el az alkalmazást Java 21-gyel:",
], "n3"));
c.push(...code([
  "java -jar reptrack-0.0.1-SNAPSHOT.jar",
]));
c.push(p("Az alkalmazás ekkor sima HTTP-n a **http://localhost:8080** címen érhető el (HTTPS-hez a Docker-es telepítést használd). A sémát a Flyway az első induláskor automatikusan létrehozza, és feltölti az 50 alap gyakorlattal."));
c.push(h2("4.1 A JAR előállítása a forrásból"));
c.push(...code([
  "export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64   # a JDK 21 helye",
  "./mvnw clean package -DskipTests   # a JAR a target/ mappába kerül",
  "./mvnw verify                      # tesztek (Docker kell hozzá)",
]));

// ---- 5. ----
c.push(h1("5. Beállítások"));
c.push(tcap("A környezeti változók és a `.env` beállításai"));
c.push(table(["Változó", "Alapérték", "Jelentés"], [
  ["`REPTRACK_ADMIN_EMAIL`, `REPTRACK_ADMIN_PASSWORD`", "(nincs)", "az első adminisztrátor adatai; csak akkor használja, ha még nincs adminisztrátor (a meglévő jelszót nem írja át)"],
  ["`REPTRACK_ADMIN_NAME`", "Adminisztrátor", "az adminisztrátor megjelenített neve"],
  ["`DB_URL`, `DB_USER`, `DB_PASSWORD`", "`jdbc:postgresql://localhost:5432/reptrack`, `reptrack`, `reptrack`", "adatbázis-hozzáférés (a Compose automatikusan beállítja); a jelszó Docker esetén csak az adatbázis első létrehozása előtt módosítható"],
  ["`HTTPS_PORT`, `HTTP_PORT`", "8443, 8080", "a Docker-futtatás gazdagépen kiadott portjai"],
  ["`REPTRACK_HTTPS_HSTS`", "false", "a HSTS fejléc; valódi domainnél és hiteles tanúsítványnál kapcsold be"],
  ["`SERVER_SSL_KEY_STORE_PASSWORD`", "(a konténer készíti)", "saját tanúsítvány esetén annak jelszava"],
  ["`SERVER_PORT`", "8080", "a HTTP-port Docker nélküli indításnál"],
], [3200, 2700, 3126], { size: 18 }));
c.push(p("", { spacing: { after: 40 } }));
c.push(p("**Saját tanúsítvány.** A konténer az első induláskor önaláírt tanúsítványt készít, véletlen jelszóval, a `reptrack-certs` kötetbe, így újraindításkor ugyanaz marad (a böngészőben egyszer elfogadott kivétel érvényes marad). Hiteles tanúsítványhoz tedd a kötetbe `reptrack.p12` néven (alias: `reptrack`), és add meg a jelszavát a `SERVER_SSL_KEY_STORE_PASSWORD` változóban."));

// ---- 6. ----
c.push(h1("6. Működés ellenőrzése"));
c.push(...bullets([
  "A **Gyakorlatok** oldalon az 50 alapgyakorlat látszik (kategória, izomcsoport szerint szűrhető).",
  "Az **Edzések → Új edzés** oldalon vegyél fel egy edzést néhány szettel. A **Befejezés** mezőt és a fáradtságot is töltsd ki: csak a lezárt edzések számítanak bele az egyéni csúcsokba és a túlterhelési szabályokba.",
  "Vegyél fel még egy, jobb eredményű edzést ugyanabból a gyakorlatból: az **Egyéni csúcsok** oldalon megjelenik az új csúcs. A **Statisztikák** oldalon a diagramok kirajzolódnak.",
  "A **Profil és célok** oldalon rögzíthető a testtömeg és az edzéscél.",
]));

// ---- 7. ----
c.push(h1("7. Biztonsági mentés és visszaállítás"));
c.push(...code([
  "# mentés:",
  "docker exec reptrack-db pg_dump -U reptrack reptrack > mentes.sql",
  "# visszaállítás (üres adatbázisba):",
  "docker exec -i reptrack-db psql -U reptrack reptrack < mentes.sql",
]));

// ---- 8. ----
c.push(h1("8. Hibaelhárítás"));
c.push(tcap("Gyakori problémák"));
c.push(table(["Tünet", "Ok és megoldás"], [
  ["`port is already allocated` vagy a 8443, 8080, 5432 foglalt", "másik program (például egy helyi PostgreSQL vagy egy korábbi fejlesztői példány) használja a portot. Állítsd le, vagy a `.env`-ben adj meg más `HTTPS_PORT` és `HTTP_PORT` értéket (az 5432-es adatbázisport a Compose fájlban rögzített)."],
  ["`docker: 'compose' is not a docker command`", "a Compose bővítmény nincs telepítve: használd az önálló `docker-compose` parancsot, vagy telepítsd a Compose bővítményt."],
  ["Böngészőfigyelmeztetés (például `ERR_CERT_AUTHORITY_INVALID`)", "várt jelenség, a tanúsítvány önaláírt. Fogadd el a „Speciális → Folytatás” gombbal. Hiteles tanúsítványt lásd az 5. fejezetben."],
  ["Az átirányítás rossz portra mutat", "a `HTTP_PORT` és a `HTTPS_PORT` értéke egyezzen a ténylegesen használt portokkal; a `.env` módosítása után `docker compose up -d` újraindítja az alkalmazást."],
  ["Az alkalmazás nem indul el, a naplóban admin-hiba szerepel", "az admin adatai hibásak (rossz e-mail-formátum, a jelszó rövidebb 8 karakternél, vagy a cím egy meglévő nem admin fiókhoz tartozik). A hibaüzenet megnevezi a javítandó változót."],
  ["`password authentication failed for user \"reptrack\"`", "az adatbázis jelszava csak az adatbázis első létrehozásakor érvényesül. Használd a régi jelszót, vagy töröld az adatokat: `docker compose down -v` (adatvesztéssel jár)."],
  ["Flyway-hiba (`checksum mismatch`) vagy séma-eltérés", "régebbi fejlesztői adatbázis maradt meg. Fejlesztési környezetben `docker compose down -v` után újraépül."],
  ["`release version 21 not supported` (helyi build)", "a `JAVA_HOME` nem JDK 21-re mutat, lásd a 4.1 szakaszt."],
  ["Formázás vagy diagramok hiányoznak", "a böngésző nem éri el a CDN-t (Bootstrap, Chart.js); internet-hozzáférés kell."],
], [3300, 5726], { size: 18 }));

// ---- 9. ----
c.push(h1("9. Eltávolítás"));
c.push(...code([
  "docker compose down -v        # konténerek, adatbázis, tanúsítvány törlése",
  "docker rmi reptrack:latest    # az alkalmazáskép törlése",
]));

// ---- dokumentum ----
function footer() {
  return new Footer({
    children: [new Paragraph({
      tabStops: [{ type: TabStopType.RIGHT, position: 9026 }],
      border: { top: { style: BorderStyle.SINGLE, size: 4, color: "BFBFBF", space: 6 } },
      children: [
        new TextRun({ text: "RepTrack – Telepítési dokumentáció", size: 18, color: "595959" }),
        new TextRun({ children: ["\t", PageNumber.CURRENT, " / ", PageNumber.TOTAL_PAGES], size: 18, color: "595959" }),
      ],
    })],
  });
}
const numCfg = (ref) => ({
  reference: ref,
  levels: [{ level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 540, hanging: 360 } } } }],
});
const doc = new Document({
  creator: "RepTrack csapat",
  title: "RepTrack – Telepítési dokumentáció",
  styles: {
    default: { document: { run: { font: L.FONT, size: 22 } } },
    paragraphStyles: [
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { font: L.FONT, size: 30, bold: true, color: L.BLUE },
        paragraph: { spacing: { before: 320, after: 140 }, keepNext: true, keepLines: true, outlineLevel: 0 } },
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { font: L.FONT, size: 25, bold: true, color: L.BLUE2 },
        paragraph: { spacing: { before: 220, after: 100 }, keepNext: true, keepLines: true, outlineLevel: 1 } },
    ],
  },
  numbering: {
    config: [
      { reference: "bullets", levels: [{ level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 540, hanging: 270 } } } }] },
      numCfg("numbers"), numCfg("n2"), numCfg("n3"),
    ],
  },
  sections: [{
    properties: { page: { size: { width: 11906, height: 16838 }, margin: { top: 1300, right: 1440, bottom: 1300, left: 1440, footer: 600 } } },
    footers: { default: footer() },
    children: c,
  }],
});
Packer.toBuffer(doc).then((buf) => {
  fs.writeFileSync(out, buf);
  console.log("kész:", out, `(${(buf.length / 1024).toFixed(0)} KB)`);
});
