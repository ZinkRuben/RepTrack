-- Gyakorlatképek a gyakorlatválasztóhoz. Az image_key a kép fájlneve kiterjesztés nélkül: a kép a
-- src/main/resources/static/images/exercises/<image_key>.jpg (vagy .jpeg, .png, .webp) fájl. Ha nincs ilyen fájl, a
-- választó a kategória ikonját mutatja, így a képek fokozatosan bővíthetők: új képhez elég a fájlt a mappába tenni
-- (a megfelelő néven) és újraépíteni az alkalmazást. A kulcs az átnevezéstől független.
-- A kulcs a név ékezet nélkül, kisbetűvel, kötőjelekkel (Ferde pados fekvenyomás -> ferde-pados-fekvenyomas);
-- kivétel a Futás, amelynek a képe szabadteri-futas néven készült.
ALTER TABLE exercises ADD COLUMN image_key VARCHAR(60);

-- csak kisbetű, számjegy és kötőjel: a kulcs egy URL része, így nem tartalmazhat / vagy .. jelet
ALTER TABLE exercises ADD CONSTRAINT ck_exercises_image_key CHECK (image_key ~ '^[a-z0-9]+(-[a-z0-9]+)*$');

UPDATE exercises e
SET image_key = v.image_key
FROM (VALUES
    ('Fekvenyomás', 'fekvenyomas'),
    ('Ferde pados fekvenyomás', 'ferde-pados-fekvenyomas'),
    ('Kézisúlyzós fekvenyomás', 'kezisulyzos-fekvenyomas'),
    ('Tárogatás kézisúlyzóval', 'tarogatas-kezisulyzoval'),
    ('Guggolás', 'guggolas'),
    ('Lábtolás', 'labtolas'),
    ('Lábnyújtás gépen', 'labnyujtas-gepen'),
    ('Lábhajlítás gépen', 'labhajlitas-gepen'),
    ('Felhúzás', 'felhuzas'),
    ('Román felhúzás', 'roman-felhuzas'),
    ('Csípőemelés rúddal', 'csipoemeles-ruddal'),
    ('Kitörés kézisúlyzóval', 'kitores-kezisulyzoval'),
    ('Döntött törzsű evezés rúddal', 'dontott-torzsu-evezes-ruddal'),
    ('Egykezes evezés kézisúlyzóval', 'egykezes-evezes-kezisulyzoval'),
    ('Mellhez húzás csigán', 'mellhez-huzas-csigan'),
    ('Ülő evezés csigán', 'ulo-evezes-csigan'),
    ('Vállból nyomás rúddal', 'vallbol-nyomas-ruddal'),
    ('Oldalemelés kézisúlyzóval', 'oldalemeles-kezisulyzoval'),
    ('Arcba húzás kötéllel', 'arcba-huzas-kotellel'),
    ('Bicepszhajlítás rúddal', 'bicepszhajlitas-ruddal'),
    ('Kalapácsbicepsz', 'kalapacsbicepsz'),
    ('Tricepsz letolás csigán', 'tricepsz-letolas-csigan'),
    ('Francia nyomás', 'francia-nyomas'),
    ('Vádliemelés állva', 'vadliemeles-allva'),
    ('Csuklóhajlítás rúddal', 'csuklohajlitas-ruddal'),
    ('Húzódzkodás', 'huzodzkodas'),
    ('Fordított fogású húzódzkodás', 'forditott-fogasu-huzodzkodas'),
    ('Ausztrál húzódzkodás', 'ausztral-huzodzkodas'),
    ('Fekvőtámasz', 'fekvotamasz'),
    ('Tolódzkodás', 'tolodzkodas'),
    ('Saját testsúlyos guggolás', 'sajat-testsulyos-guggolas'),
    ('Bolgár guggolás', 'bolgar-guggolas'),
    ('Pisztolyguggolás', 'pisztolyguggolas'),
    ('Hasprés', 'haspres'),
    ('Függő lábemelés', 'fuggo-labemeles'),
    ('Alkartámasz', 'alkartamasz'),
    ('Orosz csavarás', 'orosz-csavaras'),
    ('Négyütemű fekvőtámasz felugrással', 'negyutemu-fekvotamasz-felugrassal'),
    ('Csípőemelés hanyatt fekve', 'csipoemeles-hanyatt-fekve'),
    ('Hátfeszítés', 'hatfeszites'),
    ('Futás', 'szabadteri-futas'),
    ('Futópados futás', 'futopados-futas'),
    ('Gyaloglás', 'gyaloglas'),
    ('Túrázás', 'turazas'),
    ('Kerékpározás', 'kerekparozas'),
    ('Szobakerékpár', 'szobakerekpar'),
    ('Úszás', 'uszas'),
    ('Evezőgép', 'evezogep'),
    ('Elliptikus tréner', 'elliptikus-trener'),
    ('Ugrókötelezés', 'ugrokotelezes')
) AS v(name, image_key)
WHERE e.owner_id IS NULL AND e.name = v.name;
