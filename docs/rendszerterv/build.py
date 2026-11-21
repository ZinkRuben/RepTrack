#!/usr/bin/env python3
"""A rendszerterv előállítása (két menetben, hogy a tartalomjegyzék lapszámai pontosak legyenek).

Használat a repó gyökeréből:
    python3 docs/rendszerterv/diagrams.py            # ábrák (Graphviz + matplotlib)
    python3 docs/rendszerterv/build.py <kimenet.docx>

Előfeltétel: node + a `docx` npm modul (NODE_PATH-szal vagy `npm install docx`), LibreOffice (soffice) és
pdftotext a lapszámok kiszámításához.
1. menet: a dokumentum üres lapszámokkal készül, PDF-be konvertáljuk, és kikeressük a címsorok oldalát.
2. menet: a lapszámokkal újraépítjük (a tartalomjegyzék hossza nem változik, így az oldalak sem).
"""
import json
import os
import re
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
out = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else "Edzeskoveto_Rendszerterv.docx")
work = tempfile.mkdtemp(prefix="rendszerterv-")
pages_json = os.path.join(work, "pages.json")


def norm(s):
    return re.sub(r"\s+", " ", s).strip()


def node():
    subprocess.run(["node", os.path.join(HERE, "build.js"), out, pages_json], check=True, cwd=HERE)


def locate():
    subprocess.run(["soffice", "--headless", "--convert-to", "pdf", "--outdir", work, out], check=True,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    pdf = os.path.join(work, os.path.splitext(os.path.basename(out))[0] + ".pdf")
    text = subprocess.run(["pdftotext", "-layout", pdf, "-"], check=True, capture_output=True, text=True).stdout
    pages = [[norm(l) for l in p.split("\n") if l.strip()] for p in text.split("\f")]
    toc = json.load(open(out.replace(".docx", "") + ".toc.json", encoding="utf8"))
    result, start = {}, 2  # a címlap és a tartalomjegyzék után kezdünk keresni
    # a tartalomjegyzék oldalait átugorjuk: az első olyan oldaltól keresünk, ahol az 1. fejezet címe nem sor-végi lapszámmal áll
    first = toc[0]["text"]
    for i, lines in enumerate(pages):
        if i >= 1 and first in lines:      # a tartalomjegyzék sora „cím lapszám” alakú, ezért nem egyezik pontosan
            start = i
            break
    cur = start
    for e in toc:
        for i in range(cur, len(pages)):
            if e["text"] in pages[i]:
                result[e["text"]] = i + 1
                cur = i
                break
        else:
            print("nem található a címsor:", e["text"])
    json.dump(result, open(pages_json, "w", encoding="utf8"), ensure_ascii=False)
    return len(pages)


json.dump({}, open(pages_json, "w"))
node()
n = locate()
node()
locate()
node()
print(f"{out}  ({n} oldal)")
