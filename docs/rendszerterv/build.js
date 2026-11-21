// A rendszerterv összeállítása: node build.js <kimenet.docx> [lapszamok.json]
// Előfeltétel: `docx` npm modul (NODE_PATH vagy helyi telepítés). A lapszámokat a build.py számolja.
const fs = require("fs");
const path = require("path");
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell, WidthType, ShadingType, BorderStyle,
  AlignmentType, LevelFormat, Footer, PageNumber, PageBreak, PageOrientation, SectionType, TabStopType,
} = require("docx");
const L = require("./lib");

const out = process.argv[2] || "Edzeskoveto_Rendszerterv.docx";
const pagesFile = process.argv[3];
const pages = pagesFile && fs.existsSync(pagesFile) ? JSON.parse(fs.readFileSync(pagesFile, "utf8")) : {};
const IMG = path.join(__dirname, "img");

// ---- ábrák: sorszám és fájl ----
const FIGS = [
  "architektura", "csomagfuggosegek", "telepitesi-nezet", "kerees-utja", "pr-felismeres", "tulterheles-kiertekeles",
  "edzes-eletciklus", "szekvencia-edzes-mentese", "szekvencia-bejelentkezes", "szekvencia-statisztika",
  "szekvencia-konkurens-modositas", "adatmodell-attekintes", "er-1-felhasznalo-gyakorlat", "er-2-edzesnaplo", "oldalterkep",
];
let figCount = 0;
function FIG(key, widthCm, text, maxH) {
  figCount += 1;
  if (FIGS[figCount - 1] !== key) throw new Error(`Az ábrák sorrendje eltér: a(z) ${figCount}. ábra ${FIGS[figCount - 1]} lenne, de ${key} jött.`);
  return L.fig(path.join(IMG, key + ".png"), widthCm, text, maxH);
}

// ---- kódblokk ----
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

const ctx = { ...L, FIG, code };

// ---- tartalom (a h1/h2 hívások sorrendje adja a tartalomjegyzéket) ----
const part1 = require("./ch1_3")(ctx);
const part2 = require("./ch4_6")(ctx);
const part3 = require("./ch7_end")(ctx);
if (figCount !== FIGS.length) throw new Error(`Hiányzó ábra: ${figCount}/${FIGS.length}`);

// ---- címlap ----
const team = [
  ["Kónya Gergely", "OHUNLA"],
  ["Lenner András Tibor", "L6KOH4"],
  ["Zink Ruben", "ANZGJ8"],
  ["Garai Donát", "CRSL00"],
];
const cNone = { style: BorderStyle.NONE, size: 0, color: "FFFFFF" };
const teamTable = new Table({
  width: { size: 5600, type: WidthType.DXA },
  columnWidths: [3400, 2200],
  alignment: AlignmentType.CENTER,
  rows: [
    new TableRow({ tableHeader: true, children: ["Csapattag", "Neptun-kód"].map((h, i) => L.cell(h, [3400, 2200][i], { header: true })) }),
    ...team.map((r) => new TableRow({ children: r.map((t, i) => L.cell(t, [3400, 2200][i], { size: 22 })) })),
  ],
});

const cover = [
  new Paragraph({ spacing: { before: 2600, after: 0 }, children: [] }),
  new Paragraph({
    spacing: { after: 160 },
    children: [new TextRun({ text: "RENDSZERTERV", bold: true, size: 30, color: L.BLUE2, characterSpacing: 60 })],
  }),
  new Paragraph({
    spacing: { after: 120, line: 240 },
    children: [new TextRun({ text: "Edzéskövető és elemző alkalmazás", bold: true, size: 60, color: L.BLUE })],
  }),
  new Paragraph({
    spacing: { after: 360 },
    border: { bottom: { style: BorderStyle.SINGLE, size: 12, color: L.BLUE2, space: 10 } },
    children: [new TextRun({ text: "RepTrack – Java Spring Boot webalkalmazás", size: 32, color: "404040" })],
  }),
  new Paragraph({ spacing: { after: 60 }, children: [new TextRun({ text: "Szoftverarchitektúrák (VIAUMA21)", size: 28 })] }),
  new Paragraph({ spacing: { after: 60 }, children: [new TextRun({ text: "Házi feladat", size: 28 })] }),
  new Paragraph({ spacing: { after: 1200 }, children: [new TextRun({ text: "2026. őszi félév", size: 28 })] }),
  teamTable,
  new Paragraph({ spacing: { before: 1400, after: 0 }, children: [new TextRun({ text: "Dokumentumverzió: 1.0 · 2026. november", size: 22, color: "595959" })] }),
  new Paragraph({ children: [new PageBreak()] }),
];

const tocPage = [
  new Paragraph({
    spacing: { after: 200 },
    children: [new TextRun({ text: "Tartalomjegyzék", bold: true, size: 32, color: L.BLUE })],
  }),
  ...L.tocParagraphs(pages),
  new Paragraph({ children: [new PageBreak()] }),
];

// ---- szakaszok ----
function footer(width) {
  return new Footer({
    children: [new Paragraph({
      tabStops: [{ type: TabStopType.RIGHT, position: width }],
      border: { top: { style: BorderStyle.SINGLE, size: 4, color: "BFBFBF", space: 6 } },
      children: [
        new TextRun({ text: "RepTrack – Rendszerterv", size: 18, color: "595959" }),
        new TextRun({ children: ["\t", PageNumber.CURRENT, " / ", PageNumber.TOTAL_PAGES], size: 18, color: "595959" }),
      ],
    })],
  });
}
const portrait = { page: { size: { width: 11906, height: 16838 }, margin: { top: 1440, right: 1440, bottom: 1300, left: 1440, footer: 600 } } };
const landscapeProps = {
  page: {
    size: { width: 11906, height: 16838, orientation: PageOrientation.LANDSCAPE },
    margin: { top: 1000, right: 1000, bottom: 1000, left: 1000, footer: 500 },
  },
};

const numCfg = (ref) => ({
  reference: ref,
  levels: [{ level: 0, format: LevelFormat.DECIMAL, text: "%1.", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 540, hanging: 360 } } } }],
});

const doc = new Document({
  creator: "RepTrack csapat",
  title: "RepTrack – Rendszerterv",
  description: "Edzéskövető és elemző alkalmazás rendszerterve (Szoftverarchitektúrák, VIAUMA21)",
  styles: {
    default: { document: { run: { font: L.FONT, size: 22 } } },
    paragraphStyles: [
      { id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { font: L.FONT, size: 32, bold: true, color: L.BLUE },
        paragraph: { spacing: { before: 360, after: 160 }, keepNext: true, keepLines: true, outlineLevel: 0 } },
      { id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { font: L.FONT, size: 26, bold: true, color: L.BLUE2 },
        paragraph: { spacing: { before: 260, after: 120 }, keepNext: true, keepLines: true, outlineLevel: 1 } },
      { id: "Heading3", name: "Heading 3", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { font: L.FONT, size: 23, bold: true, color: "1F4D78" },
        paragraph: { spacing: { before: 200, after: 80 }, keepNext: true, keepLines: true, outlineLevel: 2 } },
    ],
  },
  numbering: {
    config: [
      {
        reference: "bullets",
        levels: [
          { level: 0, format: LevelFormat.BULLET, text: "•", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 540, hanging: 270 } } } },
          { level: 1, format: LevelFormat.BULLET, text: "–", alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 1080, hanging: 270 } } } },
        ],
      },
      numCfg("numbers"), numCfg("n2"), numCfg("n3"),
    ],
  },
  sections: [
    {
      properties: { ...portrait, titlePage: true },
      footers: { default: footer(9026), first: new Footer({ children: [new Paragraph({ children: [] })] }) },
      children: [...cover, ...tocPage, ...part1, ...part2.before],
    },
    {
      properties: { ...landscapeProps, type: SectionType.NEXT_PAGE },
      footers: { default: footer(13838) },
      children: part2.landscape,
    },
    {
      properties: { ...portrait, type: SectionType.NEXT_PAGE },
      footers: { default: footer(9026) },
      children: [...part2.after, ...part3],
    },
  ],
});

Packer.toBuffer(doc).then((buf) => {
  fs.writeFileSync(out, buf);
  fs.writeFileSync(out.replace(/\.docx$/, "") + ".toc.json", JSON.stringify(L.toc, null, 1));
  console.log("kész:", out, `(${(buf.length / 1024).toFixed(0)} KB, ${L.toc.length} tartalomjegyzék-sor, ${figCount} ábra)`);
});
