#!/usr/bin/env python3
"""ER-diagram generálása a Flyway migrációkból (Graphviz `dot` kell hozzá).

Használat a repó gyökeréből:  python3 docs/generate-er.py
Kimenet: docs/er-diagram.{dot,png,svg} és docs/er-diagram.md (Mermaid változattal).
Elvárás a migrációkkal szemben: egy oszlop egy sorban, a táblák `CREATE TABLE nev (` ... `);` alakban, a későbbi
oszlopok pedig `ALTER TABLE nev ADD COLUMN ...;` utasításokkal.
"""
import glob
import re
import subprocess

COLORS = {'users': '#2F5496', 'body_weight_entries': '#2F5496', 'goals': '#2F5496',
          'exercises': '#2E7D32', 'exercise_secondary_muscles': '#2E7D32',
          'workouts': '#C77700', 'workout_exercises': '#C77700', 'workout_sets': '#C77700',
          'workout_templates': '#7B3FA0', 'template_exercises': '#7B3FA0',
          'personal_records': '#B23A48', 'overload_warnings': '#B23A48'}

sql = ''.join(open(f, encoding='utf8').read() for f in sorted(glob.glob('src/main/resources/db/migration/V*.sql')))
sql = re.sub(r'--[^\n]*', '', sql)

tables, order = {}, []
for m in re.finditer(r'CREATE TABLE (\w+) \((.*?)\n\);', sql, re.S):
    name, body = m.group(1), m.group(2)
    cols, fks, pk_cols = [], [], set()
    for line in body.split('\n'):
        line = line.strip().rstrip(',')
        if not line:
            continue
        if line.startswith('PRIMARY KEY'):
            pk_cols |= set(re.findall(r'\w+', line.split('(', 1)[1]))
            continue
        if line.startswith(('CONSTRAINT', 'UNIQUE')):
            continue
        parts = line.split()
        cname, ctype = parts[0], parts[1]
        ref = re.search(r'REFERENCES (\w+)\(', line)
        pk = 'PRIMARY KEY' in line
        unique = ' UNIQUE' in line and not pk
        notnull = 'NOT NULL' in line or pk
        if ref:
            fks.append((cname, ref.group(1)))
        if ctype == 'BIGSERIAL':
            ctype = 'BIGINT'
        cols.append([cname, ctype, pk, bool(ref), unique, notnull])
    for c in cols:
        if c[0] in pk_cols:
            c[2], c[5] = True, True
    tables[name] = (cols, fks)
    order.append(name)

# későbbi migrációk ALTER TABLE ... ADD COLUMN utasításai (egy oszlop egy utasításban)
for m in re.finditer(r'ALTER TABLE (\w+)\s+ADD COLUMN (\w+) (\w+(?:\(\d+(?:,\d+)?\))?)([^;]*);', sql):
    table, cname, ctype, rest = m.groups()
    if table in tables:
        tables[table][0].append([cname, ctype, False, 'REFERENCES' in rest, False, 'NOT NULL' in rest])

d = ['digraph ER {', 'rankdir=LR;',
     'graph [fontname="Helvetica", nodesep=0.45, ranksep=1.3, bgcolor="white", splines=true, overlap=false, pad=0.3];',
     'node [shape=plain, fontname="Helvetica"];']
for t in order:
    rows = [f'<TR><TD COLSPAN="3" BGCOLOR="{COLORS.get(t, "#444")}"><FONT COLOR="white"><B>{t}</B></FONT></TD></TR>']
    for n, ty, pk, fk, u, nn in tables[t][0]:
        tag = 'PK' if pk else ('FK' if fk else ('UQ' if u else ''))
        nm = f'<B>{n}</B>' if pk else n
        rows.append(f'<TR><TD ALIGN="LEFT">{nm}</TD><TD ALIGN="LEFT"><FONT COLOR="#555555">{ty.lower()}{"" if nn else "?"}</FONT></TD><TD>{tag}</TD></TR>')
    d.append(f'{t} [label=<<TABLE BORDER="1" CELLBORDER="0" CELLSPACING="0" CELLPADDING="4" COLOR="#999999">{"".join(rows)}</TABLE>>];')
for t in order:
    for _, ref in tables[t][1]:
        d.append(f'{ref} -> {t} [arrowhead=crow, arrowtail=tee, dir=both, color="#555555"];')
d.append('}')
open('docs/er-diagram.dot', 'w').write('\n'.join(d))
subprocess.run(['dot', '-Tpng', '-Gdpi=110', 'docs/er-diagram.dot', '-o', 'docs/er-diagram.png'], check=True)
subprocess.run(['dot', '-Tsvg', 'docs/er-diagram.dot', '-o', 'docs/er-diagram.svg'], check=True)

mm = ['erDiagram']
for t in order:
    mm.append(f'    {t} {{')
    for n, ty, pk, fk, u, nn in tables[t][0]:
        tag = 'PK' if pk else ('FK' if fk else ('UK' if u else ''))
        mm.append(f'        {re.sub(r"[(),]", "_", ty).rstrip("_")} {n} {tag}'.rstrip())
    mm.append('    }')
for t in order:
    for col, ref in tables[t][1]:
        mm.append(f'    {ref} ||--o{{ {t} : "{col}"')
open('docs/er-diagram.md', 'w', encoding='utf8').write(
    '# RepTrack ER-diagram\n\nA Flyway migrációkból generálva (`python3 docs/generate-er.py`). '
    'Jelmagyarázat: PK = elsődleges kulcs, FK = idegen kulcs, UQ/UK = egyedi; `?` a PNG-ben: nullázható.\n\n'
    '![ER-diagram](er-diagram.png)\n\n```mermaid\n' + '\n'.join(mm) + '\n```\n')
print(len(order), 'tábla,', sum(len(tables[t][1]) for t in order), 'idegen kulcs')
