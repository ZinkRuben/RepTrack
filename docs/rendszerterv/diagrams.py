#!/usr/bin/env python3
"""A rendszerterv ábráinak generálása (Graphviz `dot` és matplotlib kell hozzá).

Használat a repó gyökeréből:  python3 docs/rendszerterv/diagrams.py
Kimenet: docs/rendszerterv/img/*.png

A csomagfüggőségi ábra a forráskód importjaiból készül, így mindig a valós állapotot mutatja.
"""
import collections
import glob
import html
import os
import re
import subprocess

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, Rectangle

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
IMG = os.path.join(HERE, "img")
SRC = os.path.join(ROOT, "src", "main", "java", "hu", "reptrack")
FONT = "DejaVu Sans"
plt.rcParams["font.family"] = FONT
DPI = 200

# (kitöltés, keret) – a specifikáció ábrájának színei
PAL = {
    "gray": ("#F2F2F2", "#666666"),
    "blue": ("#E8F0FA", "#2F5597"),
    "orange": ("#FFF3E0", "#C77700"),
    "green": ("#E8F5E9", "#2E7D32"),
    "purple": ("#F3E8FA", "#7B3FA0"),
    "red": ("#FBE9EC", "#B23A48"),
    "white": ("#FFFFFF", "#2F5597"),
}
INK = "#222222"


# ---------------------------------------------------------------------------------------------------------------
# matplotlib: rétegábra
# ---------------------------------------------------------------------------------------------------------------

def box(ax, x, y, w, h, color, title, lines=(), pkg=None, tsize=13, bsize=10.5, top=False):
    fc, ec = PAL[color]
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0,rounding_size=1.2", fc=fc, ec=ec, lw=2))
    items = [(title, "bold", tsize, INK, 3.0)] + [(l, "normal", bsize, INK, 2.5) for l in lines]
    if pkg:
        items.append((pkg, "normal", 9, "#555555", 2.6))
    total = sum(i[4] for i in items) - items[-1][4] * 0.4
    cy = y + h - 2.0 if top else y + h / 2 + total / 2 - 1.2
    for text, weight, size, col, step in items:
        family = "DejaVu Sans Mono" if text == pkg else FONT
        ax.text(x + w / 2, cy, text, ha="center", va="center", fontsize=size, fontweight=weight, color=col,
                family=family)
        cy -= step


def arrow(ax, p0, p1, dashed=False, head=True, lw=1.8, color="#333333"):
    ax.annotate("", xy=p1, xytext=p0, arrowprops=dict(
        arrowstyle="-|>" if head else "-", color=color, lw=lw, linestyle="--" if dashed else "-",
        shrinkA=0, shrinkB=0, mutation_scale=16))


def architecture():
    fig, ax = plt.subplots(figsize=(12, 10.8))
    ax.set_xlim(0, 120)
    ax.set_ylim(-5, 105)
    ax.axis("off")

    box(ax, 30, 91, 60, 12, "gray", "Böngésző (gép vagy telefon)", ["HTML oldalak, Bootstrap 5, Chart.js"])
    box(ax, 30, 68, 60, 17, "blue", "Webes réteg (GUI)",
        ["Spring MVC controllerek, Thymeleaf nézetek,", "űrlap-objektumok, hibakezelés"], pkg="hu.reptrack.web")

    # BLL: külső keret, két belső doboz
    fc, ec = PAL["blue"]
    ax.add_patch(FancyBboxPatch((30, 33), 60, 29, boxstyle="round,pad=0,rounding_size=1.2", fc=fc, ec=ec, lw=2))
    ax.text(60, 59.3, "Üzleti logikai réteg (BLL)", ha="center", va="center", fontsize=13, fontweight="bold",
            color=INK)
    box(ax, 33, 36, 26, 19, "white", "Szolgáltatások",
        ["használati esetek,", "tranzakció, validáció,", "tulajdonos-ellenőrzés"], pkg="…service", tsize=11.5, bsize=10)
    box(ax, 61, 36, 26, 19, "white", "Szabályok / statisztika",
        ["PR-felismerés,", "túlterhelési szabályok,", "összesítések"], pkg="…rules", tsize=10.5, bsize=10)
    arrow(ax, (59, 45.5), (61, 45.5))

    box(ax, 30, 15, 60, 14, "blue", "Adatelérési réteg (DAL)",
        ["Spring Data JPA repository-k, JPA entitások"], pkg="…repository, …domain")
    box(ax, 30, 0, 60, 11, "gray", "Adatbázis (DB)", ["PostgreSQL 16, a séma Flyway-migrációkkal verziózva"])

    box(ax, 2, 33, 22, 52, "orange", "Biztonság",
        ["Spring Security:", "", "űrlapos bejelentkezés,", "BCrypt jelszó-hash,", "CSRF-védelem,", "szerepkörök", "(USER / ADMIN)"],
        pkg="…config")
    box(ax, 96, 15, 22, 70, "green", "Közös definíciók",
        ["DTO-k, enumok,", "kivételek,", "validációs", "szabályok", "", "(JPA entitás nem", "jut ki a", "szolgáltatásokból)"],
        pkg="…dto", tsize=12)

    # rétegek közötti hívások
    ax.text(62, 88, "HTTP(S) kérés / HTML válasz", fontsize=10, style="italic", va="center", color="#444444")
    arrow(ax, (60, 91), (60, 85))
    arrow(ax, (60, 68), (60, 62))
    arrow(ax, (60, 33), (60, 29))
    arrow(ax, (60, 15), (60, 11))
    ax.text(62, 13, "JDBC", fontsize=10, style="italic", va="center", color="#444444")

    # keresztmetszeti kapcsolatok
    for y in (76.5, 47):
        arrow(ax, (24, y), (30, y), dashed=True, head=False, color="#C77700")
    for y in (76.5, 47, 22):
        arrow(ax, (90, y), (96, y), dashed=True, color="#2E7D32")

    ax.text(2, -3.5, "→  hívás / függőség iránya (csak lefelé)", fontsize=10, va="center", color="#333333")
    ax.text(44, -3.5, "- - →  használja (közös definíciók)", fontsize=10, va="center", color="#333333")
    ax.text(82, -3.5, "- - -  keresztmetszeti szempont", fontsize=10, va="center", color="#333333")
    fig.savefig(os.path.join(IMG, "architektura.png"), dpi=DPI, bbox_inches="tight", facecolor="white")
    plt.close(fig)


# ---------------------------------------------------------------------------------------------------------------
# matplotlib: szekvenciadiagram
# ---------------------------------------------------------------------------------------------------------------

def sequence(fname, parts, steps, frames=(), colw=1.5, fs=10.5, title=None):
    """parts: [(kulcs, felirat, szín)]; steps: dict-ek (k = msg|self|note), frames: (kezdő id, záró id, felirat, kulcsok)."""
    n = len(parts)
    xs = {p[0]: colw * (i + 0.5) for i, p in enumerate(parts)}
    width = colw * n
    line_h = fs * 1.55 / 72           # egy szövegsor magassága hüvelykben
    head_h = 0.62
    y = head_h + 0.25
    num = 0
    pos = {}
    layout = []
    for st in steps:
        if st["k"] == "gap":
            y += st["h"]
            continue
        lines = st["text"].count("\n") + 1
        th = lines * line_h + 0.06
        if st["k"] == "note":
            layout.append((st, y, y + th + 0.14, lines))
            pos[st.get("id")] = (y, y + th + 0.14)
            y += th + 0.14 + 0.2
        elif st["k"] == "self":
            h = max(th + 0.1, 0.3)
            layout.append((st, y, y + h, lines))
            pos[st.get("id")] = (y, y + h)
            y += h + 0.18
        else:
            layout.append((st, y, y + th + 0.06, lines))
            pos[st.get("id")] = (y, y + th + 0.06)
            y += th + 0.06 + 0.2
    total_h = y + 0.1

    fig, ax = plt.subplots(figsize=(width, total_h))
    ax.set_xlim(0, width)
    ax.set_ylim(total_h, 0)
    ax.axis("off")

    # keretek (tranzakció stb.) a vonalak alatt
    for start, end, label, keys in frames:
        x0 = min(xs[k] for k in keys) - colw * 0.47
        x1 = max(xs[k] for k in keys) + colw * 0.47
        y0 = pos[start][0] - 0.34
        y1 = pos[end][1] + 0.05
        ax.add_patch(Rectangle((x0, y0), x1 - x0, y1 - y0, fc="#FFF9E6", ec="#C77700", lw=1.3, ls="--", zorder=0))
        ax.text(x0 + 0.08, y0 + 0.07, label, fontsize=fs - 1.5, color="#8A5300", va="top", ha="left", style="italic",
                zorder=1)

    for key, label, color in parts:
        fc, ec = PAL[color]
        bw = colw * 0.94
        ax.add_patch(FancyBboxPatch((xs[key] - bw / 2, 0.05), bw, head_h, boxstyle="round,pad=0,rounding_size=0.08",
                                    fc=fc, ec=ec, lw=1.6, zorder=3))
        ax.text(xs[key], 0.05 + head_h / 2, label, ha="center", va="center", fontsize=fs - 1.5, fontweight="bold",
                color=INK, zorder=4)
        ax.plot([xs[key], xs[key]], [0.05 + head_h, total_h - 0.05], color="#8A8A8A", lw=1.0, ls=(0, (4, 3)), zorder=1)

    bbox = dict(boxstyle="square,pad=0.12", fc="white", ec="none", alpha=0.92)
    for st, y0, y1, lines in layout:
        k = st["k"]
        text = st["text"]
        if k in ("msg", "self"):
            num += 1
            text = f"{num}. {text}"
        if k == "msg":
            x_from, x_to = xs[st["f"]], xs[st["t"]]
            ya = y1 - 0.03
            mid = (x_from + x_to) / 2
            ax.text(mid, y0 + 0.02, text, ha="center", va="top", fontsize=fs, color=INK, zorder=5, bbox=bbox,
                    linespacing=1.3)
            ax.annotate("", xy=(x_to, ya), xytext=(x_from, ya), zorder=4, arrowprops=dict(
                arrowstyle="->" if st.get("dashed") else "-|>", color="#333333", lw=1.4,
                linestyle=(0, (4, 3)) if st.get("dashed") else "-", shrinkA=0, shrinkB=0, mutation_scale=13))
        elif k == "self":
            x = xs[st["who"]]
            side = st.get("side", "r")
            d = 0.28 if side == "r" else -0.28
            ym = (y0 + y1) / 2
            ax.plot([x, x + d, x + d, x], [ym - 0.1, ym - 0.1, ym + 0.1, ym + 0.1], color="#333333", lw=1.4, zorder=4)
            ax.annotate("", xy=(x, ym + 0.1), xytext=(x + d, ym + 0.1), zorder=4, arrowprops=dict(
                arrowstyle="-|>", color="#333333", lw=1.4, shrinkA=0, shrinkB=0, mutation_scale=13))
            ax.text(x + d + (0.08 if side == "r" else -0.08), ym, text, ha="left" if side == "r" else "right",
                    va="center", fontsize=fs, color=INK, zorder=5, bbox=bbox, linespacing=1.3)
        else:  # note
            x_a, x_b = xs[st["f"]], xs[st["t"]]
            x0, x1 = min(x_a, x_b) - colw * 0.4, max(x_a, x_b) + colw * 0.4
            ax.add_patch(Rectangle((x0, y0), x1 - x0, y1 - y0, fc="#FFFBE6", ec="#C77700", lw=1.2, zorder=4))
            ax.text((x0 + x1) / 2, (y0 + y1) / 2, text, ha="center", va="center", fontsize=fs - 0.5, color=INK,
                    zorder=5, linespacing=1.3)
    if title:
        ax.set_title(title, fontsize=fs + 1)
    fig.savefig(os.path.join(IMG, fname), dpi=DPI, bbox_inches="tight", facecolor="white", pad_inches=0.08)
    plt.close(fig)


def M(f, t, text, dashed=False, id=None):
    return dict(k="msg", f=f, t=t, text=text, dashed=dashed, id=id)


def S(who, text, side="r", id=None):
    return dict(k="self", who=who, text=text, side=side, id=id)


def G(h=0.3):
    return dict(k="gap", h=h)


def N(f, t, text, id=None):
    return dict(k="note", f=f, t=t, text=text, id=id)


def seq_save_workout():
    parts = [("b", "Böngésző", "gray"), ("c", "Workout-\nController", "blue"), ("s", "Workout-\nService", "blue"),
             ("p", "Personal-\nRecordService", "blue"), ("o", "Overload-\nService", "blue"),
             ("r", "Repository-k\n(PostgreSQL)", "green")]
    steps = [
        M("b", "c", "POST /workouts\n(űrlap + CSRF-token)"),
        S("c", "WorkoutForm → WorkoutRequest\n(helyi idő → Instant)"),
        M("c", "s", "create(userId, request)"),
        S("s", "@Valid és üzleti ellenőrzések:\nidők, gyakorlat láthatósága,\nszettmezők"),
        G(0.32),
        M("s", "r", "gyakorlatok betöltése,\nsaveAndFlush(workout)", id="tx0"),
        M("s", "p", "recalculate(userId,\nérintett gyakorlatok)"),
        M("p", "r", "findRecordSets:\nlezárt edzések szettjei"),
        S("p", "PersonalRecordDetector.detect()\n(tiszta függvény)", side="l"),
        M("p", "r", "régi PR-ek törlése,\núj PR-ek mentése"),
        M("s", "o", "evaluate(userId)"),
        M("o", "r", "findWorkouts,\nfindMuscleSetCounts"),
        S("o", "OverloadDetector.detect()\n+ egyeztetés a tároltakkal", side="l"),
        M("o", "r", "figyelmeztetések\nmentése / törlése", id="tx1"),
        M("s", "c", "WorkoutView", dashed=True),
        M("c", "b", "302 Redirect /workouts/{id}\n+ sikerüzenet (flash)", dashed=True),
    ]
    frames = [("tx0", "tx1", "egy adatbázis-tranzakció (@Transactional a WorkoutService.create-en)", ["s", "p", "o", "r"])]
    sequence("szekvencia-edzes-mentese.png", parts, steps, frames, colw=1.56)


def seq_login():
    parts = [("b", "Böngésző", "gray"), ("f", "Spring Security\n(szűrőlánc)", "orange"),
             ("a", "AccountDetails-\nService", "blue"), ("e", "Password-\nEncoder", "orange"),
             ("r", "UserRepository\n(PostgreSQL)", "green")]
    steps = [
        M("b", "f", "GET /workouts\n(még nincs bejelentkezve)"),
        M("f", "b", "302 Redirect /login", dashed=True),
        M("b", "f", "POST /login\n(e-mail, jelszó, CSRF-token)"),
        S("f", "CSRF-token ellenőrzése"),
        M("f", "a", "loadUserByUsername(e-mail)"),
        M("a", "r", "findByEmailIgnoreCase"),
        M("r", "a", "User", dashed=True),
        M("a", "f", "AuthenticatedUser\n(id, szerepkör, hash, enabled)", dashed=True),
        M("f", "e", "matches(jelszó, hash)\n(BCrypt)"),
        M("e", "f", "egyezik / nem egyezik", dashed=True),
        S("f", "új munkamenet (session fixation\nvédelem), SecurityContext mentése", side="r"),
        M("f", "b", "302 → eredetileg kért oldal\n(hiba esetén /login?error)", dashed=True),
    ]
    sequence("szekvencia-bejelentkezes.png", parts, steps, colw=1.62)


def seq_stats():
    parts = [("b", "Böngésző", "gray"), ("c", "Stats-\nController", "blue"), ("s", "Statistics-\nService", "blue"),
             ("k", "Statistics-\nCalculator", "purple"), ("r", "Statistics-\nRepository", "green")]
    steps = [
        M("b", "c", "GET /stats?granularity=WEEK\n&from=…&to=…&exerciseId=…"),
        S("c", "alapértelmezett időszak (Periods),\nparaméterek ellenőrzése"),
        M("c", "s", "summaries(userId, granularitás,\nidőszak, …)"),
        M("s", "r", "findWorkouts, findSets,\nfindBodyWeights (csak saját adat)"),
        M("r", "s", "WorkoutRow, SetRecord,\nBodyWeightPoint", dashed=True),
        M("s", "k", "summarize(…)"),
        M("k", "s", "List<PeriodSummary>", dashed=True),
        M("s", "c", "heti / havi összesítők", dashed=True),
        M("c", "s", "usedExercises, exerciseProgress,\nbodyWeightSeries"),
        S("c", "adatsorok összeállítása\n(címkék, értékek) → JSON", side="l"),
        M("c", "b", "HTML (stats.html) a beágyazott\nadatsorokkal", dashed=True),
        S("b", "Chart.js kirajzolja a diagramokat", side="l"),
    ]
    sequence("szekvencia-statisztika.png", parts, steps, colw=1.62)


def seq_conflict():
    parts = [("a", "Eszköz A", "gray"), ("b", "Eszköz B", "gray"), ("s", "Alkalmazás\n(WorkoutService)", "blue"),
             ("r", "PostgreSQL", "green")]
    steps = [
        M("a", "s", "GET /workouts/7/edit"),
        M("s", "a", "űrlap (version = 3)", dashed=True),
        M("b", "s", "GET /workouts/7/edit"),
        M("s", "b", "űrlap (version = 3)", dashed=True),
        M("a", "s", "POST /workouts/7  (version = 3)"),
        S("s", "3 = 3 → módosítás", side="r"),
        M("s", "r", "UPDATE workouts … version = 4\nWHERE id = 7 AND version = 3"),
        M("s", "a", "302 → az edzés oldala (siker)", dashed=True),
        M("b", "s", "POST /workouts/7  (version = 3)"),
        S("s", "3 ≠ 4 → StaleDataException", side="r"),
        M("s", "b", "409: az edzést közben máshol módosították;\ntöltsd újra, és mentsd újra", dashed=True),
    ]
    sequence("szekvencia-konkurens-modositas.png", parts, steps, colw=1.75)


# ---------------------------------------------------------------------------------------------------------------
# Graphviz
# ---------------------------------------------------------------------------------------------------------------

FS = {"title": 12, "line": 10, "edge": 10}


def head():
    return f'''
  graph [fontname="{FONT}", fontsize={FS["title"]}, bgcolor="white", pad=0.2];
  node  [fontname="{FONT}", fontsize={FS["title"]}, shape=box, style="rounded,filled", penwidth=1.8, margin="0.22,0.12"];
  edge  [fontname="{FONT}", fontsize={FS["edge"]}, color="#444444", arrowsize=0.9, penwidth=1.4];
'''
FILL = {"gray": PAL["gray"], "blue": PAL["blue"], "orange": PAL["orange"], "green": PAL["green"],
        "purple": PAL["purple"], "red": PAL["red"], "white": PAL["white"]}


def nd(name, title, lines=(), color="blue", extra=""):
    fc, ec = FILL[color]
    body = "".join(f'<br/><font point-size="{FS['line']}">{html.escape(l)}</font>' for l in lines)
    return f'  {name} [label=<<b>{html.escape(title)}</b>{body}>, fillcolor="{fc}", color="{ec}" {extra}];\n'


def dot(fname, source, engine="dot"):
    path = os.path.join(IMG, fname.replace(".png", ".dot"))
    with open(path, "w", encoding="utf8") as f:
        f.write(source)
    subprocess.run([engine, "-Tpng", f"-Gdpi={DPI}", path, "-o", os.path.join(IMG, fname)], check=True)
    os.remove(path)


def package_stats():
    pkgs = ["web", "service", "rules", "repository", "domain", "dto", "config"]
    count, deps = {}, collections.defaultdict(set)
    for p in pkgs:
        files = [f for f in glob.glob(os.path.join(SRC, p, "*.java")) if not f.endswith("package-info.java")]
        count[p] = len(files)
        for f in files:
            text = open(f, encoding="utf8").read()
            for m in re.findall(r"^import (?:static )?hu\.reptrack\.(\w+)\.", text, flags=re.M):
                if m != p:
                    deps[p].add(m)
    return count, deps


def packages():
    count, deps = package_stats()
    s = "digraph G {\n  rankdir=TB; nodesep=0.55; ranksep=0.7;\n" + head()
    s += nd("web", "web", [f"{count['web']} osztály", "controllerek, űrlapok, hibakezelés"], "blue")
    s += nd("service", "service", [f"{count['service']} osztály", "használati esetek, tranzakciók"], "blue")
    s += nd("rules", "rules", [f"{count['rules']} osztály", "PR-felismerés, szabályok, statisztika"], "purple")
    s += nd("repository", "repository", [f"{count['repository']} osztály", "Spring Data JPA, lekérdezések"], "blue")
    s += nd("domain", "domain", [f"{count['domain']} osztály", "JPA entitások"], "blue")
    s += nd("dto", "dto", [f"{count['dto']} típus", "DTO-k, enumok, kivételek"], "green")
    s += nd("config", "config", [f"{count['config']} osztály", "Security, indulási beállítások"], "orange")
    s += "  { rank=same; web; config; }\n  { rank=same; rules; repository; }\n"
    for src in ["web", "service", "rules", "repository", "domain", "config"]:
        for dst in sorted(deps[src]):
            attrs = ""
            if dst == "dto":
                attrs = ' [style=dashed, color="#2E7D32", constraint=false]'
            elif src == "web" and dst == "rules":
                attrs = ' [style=dashed, label="csak Periods"]'
            s += f"  {src} -> {dst}{attrs};\n"
    s += "}\n"
    dot("csomagfuggosegek.png", s)
    return deps


def zone(ax, x, y, w, h, color, title):
    fc, ec = PAL[color]
    ax.add_patch(FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0,rounding_size=1.5", fc=fc, ec=ec, lw=1.6, ls="-"))
    ax.text(x + 2, y + h - 2.2, title, ha="left", va="center", fontsize=11, fontweight="bold", color=ec)


def deployment():
    fig, ax = plt.subplots(figsize=(12, 9.2))
    ax.set_xlim(0, 120)
    ax.set_ylim(0, 100)
    ax.axis("off")
    zone(ax, 2, 72, 56, 27, "gray", "Felhasználó eszköze")
    box(ax, 8, 75, 44, 17, "white", "Webböngésző", ["HTML, Bootstrap 5, Chart.js", "asztali gép vagy telefon"], tsize=12)
    zone(ax, 64, 72, 54, 27, "gray", "Külső tartalomszolgáltató (CDN)")
    box(ax, 70, 75, 42, 17, "white", "jsDelivr", ["Bootstrap, Bootstrap Icons,", "Chart.js, Inter betűtípus"], tsize=12)
    zone(ax, 2, 33, 116, 33, "blue", "Docker-konténer: reptrack-app (Java 21 JRE)")
    box(ax, 8, 36, 64, 22, "white", "reptrack – Spring Boot alkalmazás",
        ["beágyazott Tomcat: HTTPS (8443-as port)", "HTTP (8080): átirányítás HTTPS-re", "web → service → repository, Flyway induláskor",
         "önaláírt tanúsítvány: reptrack-certs kötet"], tsize=12, bsize=10)
    box(ax, 80, 36, 33, 22, "orange", ".env / környezeti változók",
        ["DB_*, REPTRACK_ADMIN_*,", "HTTP_PORT, HTTPS_PORT,", "REPTRACK_HTTPS_HSTS"], tsize=11, bsize=9.5)
    zone(ax, 2, 2, 116, 26, "green", "Docker-konténer: reptrack-db")
    box(ax, 8, 5, 64, 16, "white", "PostgreSQL 16", ["reptrack adatbázis (5432-es port)"], tsize=12)
    box(ax, 80, 5, 33, 16, "white", "Docker volume", ["reptrack-db-data"], tsize=11)

    arrow2 = lambda p0, p1, dashed=False: ax.annotate("", xy=p1, xytext=p0, arrowprops=dict(
        arrowstyle="<|-|>", color="#333333", lw=1.8, linestyle="--" if dashed else "-", shrinkA=0, shrinkB=0,
        mutation_scale=16))
    arrow2((30, 75), (30, 58))
    ax.text(32, 69, "HTTPS (8443): oldalak, űrlapok, válaszok", fontsize=10, style="italic", va="center")
    arrow(ax, (52, 83.5), (70, 83.5), dashed=True)
    ax.text(61, 86, "statikus\nerőforrások", fontsize=9.5, style="italic", ha="center", va="bottom")
    arrow(ax, (80, 47), (72, 47), dashed=True)
    ax.text(76, 49.5, "konfig.", fontsize=9.5, style="italic", ha="center")
    arrow(ax, (40, 36), (40, 21))
    ax.text(42, 30.5, "JDBC (HikariCP kapcsolatkészlet)", fontsize=10, style="italic", va="center")
    arrow(ax, (72, 13), (80, 13), head=False)
    fig.savefig(os.path.join(IMG, "telepitesi-nezet.png"), dpi=DPI, bbox_inches="tight", facecolor="white")
    plt.close(fig)


def request_flow():
    s = "digraph R {\n  rankdir=TB; nodesep=0.5; ranksep=0.42;\n" + head()
    s += nd("browser", "Böngésző", ["HTTP(S) kérés"], "gray")
    s += nd("csrf", "CSRF-szűrő", ["POST-nál a token ellenőrzése"], "orange")
    s += nd("auth", "Hitelesítés", ["munkamenet vagy űrlapos bejelentkezés", "(AccountDetailsService + BCrypt)"], "orange")
    s += nd("authz", "Jogosultság", ["URL-szabályok: nyilvános / USER / ADMIN", "(szerepkör-hierarchia)"], "orange")
    s += nd("disp", "DispatcherServlet", ["útvonal → controller"], "blue")
    s += nd("ctrl", "Controller", ["paraméter- és űrlapkötés, @Valid", "a bejelentkezett felhasználó azonosítója"], "blue")
    s += nd("svc", "Service", ["@Transactional, @Validated", "tulajdonos-ellenőrzés, üzleti szabályok"], "blue")
    s += nd("repo", "Repository", ["paraméterezett lekérdezések (JPA)"], "blue")
    s += nd("db", "PostgreSQL", ["CHECK / UNIQUE / FK megszorítások"], "green")
    s += nd("view", "Thymeleaf nézet", ["HTML előállítása (kimenet-escape),", "a válasz a böngészőnek megy"], "blue")
    s += nd("err", "GlobalExceptionHandler", ["NotFound → 404, BusinessRule → 400,", "StaleData → 409: hibaoldal a böngészőnek"], "red")
    s += '''  subgraph cluster_sec { label=<<b>Spring Security szűrőlánc</b>>; style="rounded,dashed"; color="#C77700"; penwidth=1.4;
    csrf; auth; authz; }
'''
    s += "  browser -> csrf -> auth -> authz -> disp -> ctrl -> svc -> repo -> db;\n"
    s += '  ctrl -> view [label="  DTO a modellben", constraint=false];\n'
    s += '  svc -> err [label="  kivétel", style=dashed, constraint=false];\n'
    s += "  { rank=same; ctrl; view; }\n  { rank=same; svc; err; }\n}\n"
    dot("kerees-utja.png", s)


def pr_flow():
    s = "digraph P {\n  rankdir=TB; nodesep=0.5; ranksep=0.38;\n" + head()
    s += nd("in", "Bemenet", ["egy gyakorlat szettjei a felhasználó", "lezárt edzéseiből (findRecordSets)"], "gray")
    s += nd("sort", "Időrendbe rendezés, edzésenkénti csoportosítás", [], "blue")
    s += nd("cand", "Jelöltek képzése a szettekből", [
        "súlyzós / saját testsúlyos: legnagyobb súly adott ismétlésszámnál",
        "súlyzós: szettvolumen; becsült 1RM (Epley, legfeljebb 10 ismétlés)",
        "futás: átlagtempó a távsávban (1 / 5 / 10 km, félmaraton, maraton)"], "blue")
    s += nd("best", "Edzésenként és rekordkulcsonként a legjobb jelölt", ["(egyenlő eredménynél a korábbi szett)"], "blue")
    s += '  q1 [label="Van korábbi legjobb\\nerre a kulcsra?", shape=diamond, style="filled", fillcolor="#FFF3E0", color="#C77700", penwidth=1.8, margin="0.1,0.05"];\n'
    s += '  q2 [label="Szigorúan jobb\\na korábbinál?", shape=diamond, style="filled", fillcolor="#FFF3E0", color="#C77700", penwidth=1.8, margin="0.1,0.05"];\n'
    s += nd("base", "Alapérték", ["nem PR (nincs mihez hasonlítani)"], "gray")
    s += nd("pr", "Új egyéni csúcs", ["rögzítés a rekordot elérő szettel,", "a legjobb érték frissítése"], "red")
    s += nd("none", "Nem csúcs", ["a legjobb érték változatlan"], "gray")
    s += nd("out", "Kimenet", ["PersonalRecordService: a gyakorlat régi PR-jeinek", "törlése és az újak mentése (teljes újraszámolás)"], "green")
    s += "  in -> sort -> cand -> best -> q1;\n"
    s += '  q1 -> base [label=" nincs"];\n  q1 -> q2 [label=" van"];\n'
    s += '  q2 -> pr [label=" igen"];\n  q2 -> none [label=" nem"];\n'
    s += "  base -> out;\n  pr -> out;\n  none -> out;\n"
    s += "  { rank=same; base; q2; }\n  { rank=same; pr; none; }\n}\n"
    dot("pr-felismeres.png", s)


def overload_flow():
    s = "digraph O {\n  rankdir=TB; nodesep=0.4; ranksep=0.4;\n" + head()
    s += nd("in", "Bemenet", ["a felhasználó lezárt edzései (kezdés, vég, fáradtság)", "és az izomcsoportonkénti szettszámok", "+ a profilban beállított küszöbök"], "gray")
    s += nd("r1", "WEEKLY_LOAD", ["a hét eddigi terhelése > az előző", "legfeljebb 4 hét átlagának 130%-a", "(legalább 3 előzményhét kell)"], "purple")
    s += nd("r2", "MUSCLE_SETS", ["ugyanaz az elsődleges izomcsoport", "3 egymást követő napon,", "naponta legalább 6 szett"], "purple")
    s += nd("r3", "HIGH_FATIGUE", ["3 egymást követő edzés", "fáradtsága legalább 8"], "purple")
    s += nd("find", "Találatok", ["szabály, kulcs (pl. week:2026-11-16), esemény dátuma,", "edzés, üzenet; szűrés a 90 napos horizontra"], "blue")
    s += nd("n", "Új találat", ["figyelmeztetés létrehozása", "(nyugtázatlan)"], "red")
    s += nd("u", "Már tárolt találat", ["üzenet és dátum frissítése,", "a nyugtázott állapot megmarad"], "blue")
    s += nd("d", "Tárolt, de már nem áll fenn", ["törlés (ha a horizonton belül van)"], "gray")
    s += nd("out", "Eredmény", ["az edzés oldalán és az edzéslistán", "(nyitottak) jelennek meg; nyugtázható"], "green")
    s += "  in -> r1; in -> r2; in -> r3;\n  r1 -> find; r2 -> find; r3 -> find;\n"
    s += '  find -> n [label=" egyezés a tároltakkal"]; find -> u; find -> d;\n'
    s += "  n -> out; u -> out; d -> out;\n  { rank=same; r1; r2; r3; }\n  { rank=same; n; u; d; }\n}\n"
    dot("tulterheles-kiertekeles.png", s)


def workout_lifecycle():
    s = "digraph W {\n  rankdir=TB; nodesep=1.4; ranksep=0.75;\n" + head()
    s += '  start [label="", shape=circle, width=0.22, style=filled, fillcolor="#333333", color="#333333"];\n'
    s += nd("open", "Nyitott edzés", ["befejezés nincs megadva,", "a fáradtság nem kötelező;", "nem számít a PR-ekbe és", "a túlterhelési szabályokba"], "gray")
    s += nd("closed", "Lezárt edzés", ["befejezés és fáradtság (1–10) megadva,", "a befejezés a kezdés után van,", "legfeljebb 24 órával;", "beleszámít a PR-ekbe, a statisztikákba", "és a szabályokba"], "blue")
    s += '  end [label="", shape=doublecircle, width=0.22, style=filled, fillcolor="#333333", color="#333333"];\n'
    s += '  start -> open [label="  új edzés\\n  (üres vagy másolat)"];\n'
    s += '  open -> closed [label="lezárás:\\nbefejezés és\\nfáradtság megadása"];\n'
    s += '  closed -> open [label="újranyitás:\\nbefejezés törlése"];\n'
    s += '  start -> closed [label="új edzés\\nbefejezéssel", constraint=false];\n'
    s += '  open -> end [label="  törlés"];\n'
    s += '  closed -> end [label="  törlés"];\n'
    s += "  { rank=same; open; closed; }\n}\n"
    dot("edzes-eletciklus.png", s)


def sitemap():
    FS.update(title=15, line=12, edge=12)
    s = "digraph M {\n  rankdir=LR; nodesep=0.22; ranksep=0.45;\n" + head()
    s += nd("home", "/", ["Nyitóoldal: vendégnek", "bemutatkozás, belépettnek", "gyors műveletek"], "gray")
    s += nd("login", "/login", ["Bejelentkezés"], "orange")
    s += nd("reg", "/register", ["Regisztráció"], "orange")
    s += nd("list", "/workouts", ["Edzésnapló: lista, szűrés,", "lapozás, figyelmeztetések"], "blue")
    s += nd("new", "/workouts/new", ["Új edzés"], "blue")
    s += nd("det", "/workouts/{id}", ["Részletek, új csúcsok,", "figyelmeztetések"], "blue")
    s += nd("edit", "/workouts/{id}/edit", ["Szerkesztés"], "blue")
    s += nd("stats", "/stats", ["Statisztikák, diagramok"], "purple")
    s += nd("rec", "/records", ["Egyéni csúcsok"], "red")
    s += nd("ex", "/exercises", ["Gyakorlatok böngészése, kezelése"], "green")
    s += nd("prof", "/profile", ["Profil, testtömeg, célok"], "blue")
    s += nd("exform", "/exercises/new", ["Gyakorlat-űrlap (saját gyakorlat)"], "green")
    s += nd("adm", "/admin/exercises/**", ["Közös gyakorlatok kezelése", "(csak ADMIN)"], "green")
    s += nd("tpl", "/templates", ["Edzéssablonok: lista,", "edzés indítása"], "blue")
    s += nd("tplform", "/templates/new", ["Sablon-űrlap (új, szerkesztés;", "előtöltve egy edzésből)"], "blue")
    s += nd("accpw", "/account/password", ["Jelszó módosítása"], "orange")
    s += nd("accdel", "/account/delete", ["Fiók törlése", "(megerősítés jelszóval)"], "orange")
    s += nd("admu", "/admin/users", ["Felhasználók kezelése:", "letiltás, jelszó-visszaállítás,", "kinevezés, törlés (csak ADMIN)"], "orange")
    s += "  home -> login; home -> reg; home -> list; home -> stats; home -> rec; home -> ex; home -> prof;\n"
    s += "  prof -> accpw; prof -> accdel; home -> admu [label=\" ADMIN\"];\n"
    s += '  login -> accpw [style=dashed, label=" ideiglenes jelszóval:\\n csak ez érhető el"];\n'
    s += "  ex -> exform; ex -> adm [label=\" ADMIN\"];\n  list -> new; list -> det; det -> edit;\n"
    s += '  det -> edit [style=dashed, label=" másolás: új, nyitott\\n edzés szerkesztője"];\n'
    s += "  home -> tpl; list -> tpl [label=\" Sablonból\"]; tpl -> tplform;\n"
    s += '  tpl -> new [style=dashed, label=" edzés indítása:\\n előtöltött űrlap"];\n'
    s += '  det -> tplform [style=dashed, label=" mentés sablonként"];\n}\n'
    dot("oldalterkep.png", s)
    FS.update(title=12, line=10, edge=10)


def domain_overview():
    FS.update(title=14, line=12, edge=10)
    s = "digraph E {\n  rankdir=TB; nodesep=0.2; ranksep=0.55;\n" + head()
    def n(name, title, table, color):
        return nd(name, title, [table], color)
    s += n("users", "Felhasználó", "users", "blue")
    s += n("bw", "Testtömeg-napló", "body_weight_entries", "blue")
    s += n("goals", "Edzéscél", "goals", "blue")
    s += n("ex", "Gyakorlat", "exercises", "green")
    s += n("sec", "Másodlagos izom", "exercise_secondary_muscles", "green")
    s += n("w", "Edzés", "workouts", "orange")
    s += n("we", "Edzés gyakorlata", "workout_exercises", "orange")
    s += n("ws", "Szett", "workout_sets", "orange")
    s += n("wt", "Edzéssablon", "workout_templates", "purple")
    s += n("te", "Sablon gyakorlata", "template_exercises", "purple")
    s += n("pr", "Egyéni csúcs", "personal_records", "red")
    s += n("ow", "Figyelmeztetés", "overload_warnings", "red")
    edges = [("users", "bw"), ("users", "goals"), ("users", "w"), ("users", "wt"), ("users", "ex"),
             ("ex", "sec"), ("ex", "we"), ("ex", "goals"), ("ex", "te"), ("ex", "pr"), ("w", "we"), ("we", "ws"),
             ("wt", "te"), ("ws", "pr"), ("w", "ow"), ("users", "pr"), ("users", "ow")]
    for a, b in edges:
        s += f'  {a} -> {b} [arrowhead=crow, arrowtail=tee, dir=both, color="#555555", penwidth=1.1, arrowsize=0.7];\n'
    s += "  { rank=same; bw; goals; w; wt; ex; }\n  { rank=same; we; te; sec; pr; ow; }\n}\n"
    dot("adatmodell-attekintes.png", s)
    FS.update(title=12, line=10, edge=10)


def parse_schema():
    """A Flyway migrációkból kiolvassa a táblákat (ugyanaz a logika, mint a docs/generate-er.py-ban)."""
    sql = "".join(open(f, encoding="utf8").read()
                  for f in sorted(glob.glob(os.path.join(ROOT, "src", "main", "resources", "db", "migration", "V*.sql"))))
    sql = re.sub(r"--[^\n]*", "", sql)
    tables, order = {}, []
    for m in re.finditer(r"CREATE TABLE (\w+) \((.*?)\n\);", sql, re.S):
        name, body = m.group(1), m.group(2)
        cols, fks, pk_cols = [], [], set()
        for line in body.split("\n"):
            line = line.strip().rstrip(",")
            if not line:
                continue
            if line.startswith("PRIMARY KEY"):
                pk_cols |= set(re.findall(r"\w+", line.split("(", 1)[1]))
                continue
            if line.startswith(("CONSTRAINT", "UNIQUE")):
                continue
            parts = line.split()
            cname, ctype = parts[0], parts[1]
            ref = re.search(r"REFERENCES (\w+)\(", line)
            pk = "PRIMARY KEY" in line
            unique = " UNIQUE" in line and not pk
            notnull = "NOT NULL" in line or pk
            if ref:
                fks.append((cname, ref.group(1)))
            if ctype == "BIGSERIAL":
                ctype = "BIGINT"
            cols.append([cname, ctype, pk, bool(ref), unique, notnull])
        for c in cols:
            if c[0] in pk_cols:
                c[2], c[5] = True, True
        tables[name] = (cols, fks)
        order.append(name)
    for m in re.finditer(r"ALTER TABLE (\w+)\s+ADD COLUMN (\w+) (\w+(?:\(\d+(?:,\d+)?\))?)([^;]*);", sql):
        table, cname, ctype, rest = m.groups()
        if table in tables:
            tables[table][0].append([cname, ctype, False, "REFERENCES" in rest, False, "NOT NULL" in rest])
    return tables, order


ER_COLORS = {"users": "#2F5497", "body_weight_entries": "#2F5497", "goals": "#2F5497",
             "exercises": "#2E7D32", "exercise_secondary_muscles": "#2E7D32",
             "workouts": "#C77700", "workout_exercises": "#C77700", "workout_sets": "#C77700",
             "workout_templates": "#7B3FA0", "template_exercises": "#7B3FA0",
             "personal_records": "#B23A48", "overload_warnings": "#B23A48"}


def er_part(fname, show, stubs, rankdir="TB", extra=""):
    tables, order = parse_schema()
    d = ["digraph ER {", f"rankdir={rankdir};",
         f'graph [fontname="{FONT}", nodesep=0.5, ranksep=0.8, bgcolor="white", pad=0.2{extra}];',
         f'node [shape=plain, fontname="{FONT}", fontsize=11];',
         f'edge [color="#555555", arrowhead=crow, arrowtail=tee, dir=both, penwidth=1.2, arrowsize=0.8, fontname="{FONT}", fontsize=9];']
    for t in show + stubs:
        head = ER_COLORS.get(t, "#444444")
        rows = [f'<TR><TD COLSPAN="3" BGCOLOR="{head}"><FONT COLOR="white"><B>{t}</B></FONT></TD></TR>']
        cols = tables[t][0]
        if t in stubs:
            cols = [c for c in cols if c[2]][:1]
        for n, ty, pk, fk, u, nn in cols:
            tag = "PK" if pk else ("FK" if fk else ("UQ" if u else ""))
            nm = f"<B>{n}</B>" if pk else n
            rows.append(f'<TR><TD ALIGN="LEFT">{nm}</TD><TD ALIGN="LEFT"><FONT COLOR="#555555">{ty.lower()}{"" if nn else "?"}</FONT></TD><TD>{tag}</TD></TR>')
        if t in stubs:
            rows.append('<TR><TD COLSPAN="3" ALIGN="LEFT"><FONT COLOR="#777777"><I>… (másik ábrán)</I></FONT></TD></TR>')
        d.append(f'{t} [label=<<TABLE BORDER="1" CELLBORDER="0" CELLSPACING="0" CELLPADDING="3" COLOR="#999999">{"".join(rows)}</TABLE>>];')
    names = set(show + stubs)
    for t in show:
        for col, ref in tables[t][1]:
            if ref in names:
                d.append(f"{ref} -> {t};")
    d.append("}")
    path = os.path.join(IMG, fname.replace(".png", ".dot"))
    open(path, "w", encoding="utf8").write("\n".join(d))
    subprocess.run(["dot", "-Tpng", f"-Gdpi={DPI}", path, "-o", os.path.join(IMG, fname)], check=True)
    os.remove(path)


def er_diagrams():
    er_part("er-1-felhasznalo-gyakorlat.png",
            ["users", "body_weight_entries", "goals", "exercises", "exercise_secondary_muscles", "workout_templates",
             "template_exercises"], [], "LR", ", nodesep=0.35, ranksep=0.9")
    er_part("er-2-edzesnaplo.png",
            ["workouts", "workout_exercises", "workout_sets", "personal_records", "overload_warnings"],
            ["users", "exercises"], "LR", ", nodesep=0.35, ranksep=0.9")


def main():
    os.makedirs(IMG, exist_ok=True)
    architecture()
    seq_save_workout()
    seq_login()
    seq_stats()
    seq_conflict()
    packages()
    deployment()
    request_flow()
    pr_flow()
    overload_flow()
    workout_lifecycle()
    sitemap()
    domain_overview()
    er_diagrams()
    # az ER-diagramot a docs/generate-er.py készíti; a rendszerterv ugyanazt használja
    print("kész:", ", ".join(sorted(os.listdir(IMG))))


if __name__ == "__main__":
    main()
