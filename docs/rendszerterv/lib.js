// A rendszerterv-generátor segédfüggvényei (docx-js). A tartalom a build.js-ben van.
const fs = require("fs");
const path = require("path");
const {
  Paragraph, TextRun, ImageRun, Table, TableRow, TableCell, WidthType, ShadingType, BorderStyle, AlignmentType,
  HeadingLevel, TabStopType, LevelFormat, PositionalTab, PositionalTabAlignment, PositionalTabRelativeTo,
  PositionalTabLeader,
} = require("docx");

const FONT = "Calibri";
const MONO = "Courier New";
const BLUE = "1F3864";
const BLUE2 = "2F5496";
const HEAD_FILL = "D9E2F3";
const PAGE_W = 9026; // A4, 2,54 cm margó: 11906 - 2 * 1440

// ---- szöveg: **félkövér**, `kód`, __dőlt__ ----
function runs(text, base = {}) {
  const out = [];
  const re = /(\*\*[^*]+\*\*|`[^`]+`|__[^_]+__)/g;
  let last = 0;
  let m;
  while ((m = re.exec(text)) !== null) {
    if (m.index > last) out.push(new TextRun({ text: text.slice(last, m.index), ...base }));
    const tok = m[0];
    if (tok.startsWith("**")) out.push(new TextRun({ text: tok.slice(2, -2).replace(/`/g, ""), ...base, bold: true }));
    else if (tok.startsWith("`")) out.push(new TextRun({ ...base, text: tok.slice(1, -1), font: MONO, size: (base.size || 22) - 2 }));
    else out.push(new TextRun({ text: tok.slice(2, -2), italics: true, ...base }));
    last = m.index + tok.length;
  }
  if (last < text.length) out.push(new TextRun({ text: text.slice(last), ...base }));
  return out;
}

function p(text, opts = {}) {
  const { size, bold, color, ...rest } = opts;
  const base = {};
  if (size) base.size = size;
  if (bold) base.bold = true;
  if (color) base.color = color;
  return new Paragraph({ children: runs(text, base), spacing: { after: 120, line: 276 }, ...rest });
}

// ---- címsorok (a tartalomjegyzékhez gyűjtjük) ----
const toc = [];
function h1(text) {
  text = text.replace(/`/g, "");
  toc.push({ level: 1, text });
  return new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun(text)] });
}
function h2(text) {
  text = text.replace(/`/g, "");
  toc.push({ level: 2, text });
  return new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun(text)] });
}
function h3(text) {
  text = text.replace(/`/g, "");
  return new Paragraph({ heading: HeadingLevel.HEADING_3, children: [new TextRun(text)] });
}

// ---- listák ----
function bullets(items, level = 0) {
  return items.map((t) => new Paragraph({
    numbering: { reference: "bullets", level },
    children: runs(t),
    spacing: { after: 60, line: 264 },
  }));
}
function numbered(items, ref = "numbers") {
  return items.map((t) => new Paragraph({
    numbering: { reference: ref, level: 0 },
    children: runs(t),
    spacing: { after: 60, line: 264 },
  }));
}

// ---- megjegyzés-doboz ----
function note(text, color = "2F5496", fill = "EAF1FB") {
  return new Paragraph({
    children: runs(text, { size: 21 }),
    spacing: { before: 80, after: 160, line: 264 },
    indent: { left: 200, right: 100 },
    shading: { type: ShadingType.CLEAR, fill, color: "auto" },
    border: { left: { style: BorderStyle.SINGLE, size: 18, color, space: 8 } },
  });
}

// ---- táblázat ----
const border = { style: BorderStyle.SINGLE, size: 4, color: "BFBFBF" };
const borders = { top: border, bottom: border, left: border, right: border };
const STATUS_FILL = { "Kész": "E2F0D9", "Részben": "FFF2CC", "Tervezett": "FCE4D6" };

function cell(content, width, o = {}) {
  const items = Array.isArray(content) ? content : [content];
  const size = o.size || 20;
  const fill = o.fill || (o.header ? HEAD_FILL : undefined);
  const paras = items.map((t, i) => new Paragraph({
    children: runs(String(t), { size, bold: o.header || o.bold }),
    spacing: { after: i === items.length - 1 ? 0 : 40, line: 252 },
    alignment: o.align || AlignmentType.LEFT,
  }));
  return new TableCell({
    width: { size: width, type: WidthType.DXA },
    borders,
    shading: fill ? { type: ShadingType.CLEAR, fill, color: "auto" } : undefined,
    margins: { top: 60, bottom: 60, left: 100, right: 100 },
    children: paras,
  });
}

// widths: DXA, összegük PAGE_W (vagy a megadott total)
function table(headers, rows, widths, o = {}) {
  const total = widths.reduce((a, b) => a + b, 0);
  const head = new TableRow({
    tableHeader: true,
    cantSplit: true,
    children: headers.map((h, i) => cell(h, widths[i], { header: true, size: o.size })),
  });
  const body = rows.map((r) => new TableRow({
    cantSplit: true,
    children: r.map((c, i) => {
      let fill;
      let text = c;
      if (o.statusCol === i && typeof c === "string") fill = STATUS_FILL[c];
      if (c && typeof c === "object" && !Array.isArray(c)) { text = c.t; fill = c.fill || fill; }
      return cell(text, widths[i], { size: o.size, fill, bold: o.boldFirst && i === 0 });
    }),
  }));
  return new Table({
    width: { size: total, type: WidthType.DXA },
    columnWidths: widths,
    rows: [head, ...body],
  });
}

let tableNo = 0;
function caption(text) {
  return new Paragraph({
    children: [new TextRun({ text, italics: true, size: 20, color: "404040" })],
    alignment: AlignmentType.CENTER,
    spacing: { before: 60, after: 200 },
  });
}
function tcap(text) {
  tableNo += 1;
  return new Paragraph({
    keepNext: true,
    children: [new TextRun({ text: `${tableNo}. táblázat: ${text}`, italics: true, size: 20, color: "404040" })],
    spacing: { before: 160, after: 80 },
  });
}
function spacer(after = 120) {
  return new Paragraph({ children: [], spacing: { after } });
}

// ---- ábrák ----
function pngSize(file) {
  const b = fs.readFileSync(file);
  return { w: b.readUInt32BE(16), h: b.readUInt32BE(20) };
}
let figNo = 0;
// A kép a megadott szélességre skálázódik, de nem lehet magasabb maxH-nál (cm).
function fig(file, widthCm, text, maxHcm = 21) {
  const { w, h } = pngSize(file);
  let wc = widthCm;
  let hc = (wc * h) / w;
  if (hc > maxHcm) { hc = maxHcm; wc = (hc * w) / h; }
  const px = (cm) => Math.round((cm / 2.54) * 96);
  figNo += 1;
  return [
    new Paragraph({
      keepNext: true,
      alignment: AlignmentType.CENTER,
      spacing: { before: 120, after: 60 },
      children: [new ImageRun({
        type: "png",
        data: fs.readFileSync(file),
        transformation: { width: px(wc), height: px(hc) },
        altText: { title: text, description: text, name: path.basename(file) },
      })],
    }),
    caption(`${figNo}. ábra: ${text}`),
  ];
}

// ---- tartalomjegyzék (statikus, a lapszámok a lap-térképből) ----
function tocParagraphs(pages) {
  const out = [];
  for (const e of toc) {
    const pageNo = pages[e.text] !== undefined ? String(pages[e.text]) : "";
    out.push(new Paragraph({
      spacing: { before: e.level === 1 ? 70 : 0, after: e.level === 1 ? 10 : 0, line: 228 },
      indent: { left: e.level === 1 ? 0 : 360 },
      keepLines: true,
      tabStops: [{ type: TabStopType.RIGHT, position: PAGE_W, leader: "dot" }],
      children: [
        new TextRun({ text: e.text, bold: e.level === 1, size: e.level === 1 ? 21 : 19, color: e.level === 1 ? BLUE : "000000" }),
        new TextRun({ text: "\t" + pageNo, bold: e.level === 1, size: e.level === 1 ? 21 : 19 }),
      ],
    }));
  }
  return out;
}

module.exports = {
  FONT, MONO, BLUE, BLUE2, HEAD_FILL, PAGE_W, runs, p, h1, h2, h3, bullets, numbered, note, table, cell, tcap,
  caption, spacer, fig, toc, tocParagraphs, STATUS_FILL,
};
