# Moje kořeny – návrh Android aplikace

Genealogická aplikace pro Android, napojená na rodinný web (Astro + Cloudflare Workers).
Funguje i bez připojení, umí GEDCOM a fotografie u osob. Po přihlášení
jde záznamy upravovat a změny se přenesou zpět na web.

---

## 1. Výchozí stav webu (z druhé session)

| Část | Dnes |
|---|---|
| Zdroj pravdy | soubory v projektu na Macu: `data/osoby.py`, `data/zaznamy.json`, `data/prepisy.json`, `data/mista.json`, `data/soukrome_doplnky.json`, `data/web.json` |
| Sestavení | `tools/export_web.py` → `web/src/data/site.json` → Astro → statické stránky `web/dist` |
| GEDCOM | `tools/ged_export.py` → `/stazeni/rodokmen.ged` (úplný, za heslem HTTP Basic) |
| Hosting | Cloudflare Worker (`web/cloudflare/worker.js`) + statické soubory (assets) |
| Přihlášení | rodinná otázka → cookie (volitelně), stažení GEDCOM za heslem (v projektu jen SHA-256 otisk) |
| Identita osob | stabilní „slug“, např. `jan-novak-1849`; rodiny `otec-…-a-matka-…`; zápisy z matrik s vlastním ID |

**Důsledek:** web je dnes jen ke čtení – nemá databázi ani API, kam by aplikace mohla
zapisovat. Pro editaci z mobilu je potřeba přidat malé API na stejný Worker.
Návrh to řeší tak, aby se stávající tok (data na Macu → web) nerozbil.

---

## 2. Co aplikace umí (rozsah)

### MVP (verze 1.0)
1. **Přihlášení** k webu (jméno + heslo → token). Bez přihlášení jde aplikace používat
   jen s lokálními / importovanými daty.
2. **Stažení rodokmenu z webu** a uložení do telefonu → plně funkční **offline**.
3. **Prohlížení:** seznam osob s vyhledáváním (bez diakritiky), detail osoby
   (data, místa, fakta, poznámky, rodiče, partneři, děti, matriční zápisy se shrnutím,
   přepisem a odkazem na snímek v e-badatelně), **vývod / rozrod** jako strom.
4. **Editace offline:** úprava osoby, přidání osoby, partnera, dítěte, rodiče, faktu,
   poznámky, zdroje. Změny se zařadí do fronty a odešlou se, až bude připojení.
5. **Fotografie:** přidání z galerie nebo fotoaparátu, oříznutí, popisek, rok,
   označení více osob na jedné fotce, nastavení jako portrét.
6. **GEDCOM:** import `.ged` (5.5.1 i 7.0) a export `.ged` / `.zip` (GEDCOM + fotky),
   sdílení přes Android (e-mail, Disk, …).

### Později (1.x)
- Konflikty upravené na dvou místech zároveň – porovnání a výběr verze (viz 5.4).
- Zobrazení míst na mapě, časová osa osoby, výročí (notifikace „dnes by měl 150 let“).
- Více rodokmenů v aplikaci (např. importovaný strom od příbuzného vedle vlastního).
- Porovnání GEDCOM s daty z webu (najít nové osoby / rozdíly).
- Zámek aplikace biometrikou.

---

## 3. Architektura

```
┌──────────────────── Android: Moje kořeny ─────────────────────┐
│  UI (Jetpack Compose, Material 3)                             │
│    Osoby · Detail · Strom · Fotky · Import/Export · Nastavení │
│  ViewModely (Kotlin Flow)                                     │
│  Repozitáře ──────────────┬───────────────────┐               │
│  Room (SQLite) = offline  │  Fronta změn      │  Soubory fotek│
│  pravda v telefonu        │  (outbox)         │  (app storage)│
│                           └── SyncWorker (WorkManager) ───┐   │
│  GEDCOM parser / writer (vlastní modul, čisté Kotlin/JVM)  │   │
└────────────────────────────────────────────────────────────┼──┘
                                                             │ HTTPS + token
┌──────────────── Cloudflare (stávající Worker) ─────────────▼──┐
│  /…            statický web (beze změny)                      │
│  /stazeni/…    GEDCOM za heslem (beze změny)                  │
│  /api/v1/…     NOVÉ: přihlášení, data, změny, fotky           │
│        D1 (SQLite): uživatelé, změny (changes), stav          │
│        R2: fotografie a náhledy                               │
└───────────────────────────────┬───────────────────────────────┘
                                │ tools/stahni_zmeny.py (na Macu)
┌───────────────────────────────▼───────────────────────────────┐
│  Projekt na Macu: data/*.json → export_web.py → nasazení      │
└───────────────────────────────────────────────────────────────┘
```

### 3.1 Technologie aplikace

| Oblast | Volba | Proč |
|---|---|---|
| Jazyk | Kotlin 2.x | standard pro Android |
| UI | Jetpack Compose + Material 3 (dynamické barvy) | moderní, méně kódu |
| Min. Android | 8.0 (API 26), cíl API 36 | pokryje >95 % telefonů |
| Databáze | Room (SQLite) + FTS4 pro hledání | offline, rychlé hledání |
| Síť | Ktor client / Retrofit + kotlinx.serialization | JSON API |
| Synchronizace | WorkManager (jen při síti, s opakováním) | spolehlivé i po restartu |
| Obrázky | Coil 3, Photo Picker, CameraX, uCrop | náhledy, výběr, ořez |
| DI | Hilt | |
| Bezpečné úložiště | DataStore + Android Keystore (šifrovaný token) | |
| Strom | vlastní Compose `Canvas` (zoom, posun) | knihovny pro rodokmen chybí |
| GEDCOM | vlastní modul `:gedcom` (parser + writer) | Java knihovny jsou zastaralé, chybí GEDCOM 7 a CONC/CONT/UTF-8 detaily |
| Build | Gradle (KTS), GitHub Actions → podepsané APK / AAB | |

### 3.2 Moduly projektu

```
app/            UI, navigace, DI
core/model/     doménové třídy (Person, Family, Event, Source, Record, Media, Place)
core/data/      Room, repozitáře, mapování
core/sync/      API klient, SyncWorker, řešení konfliktů
gedcom/         čistý Kotlin: GedcomReader, GedcomWriter, mapování na model (unit testy)
feature/…       osoby, detail, strom, fotky, import-export, prihlaseni, nastaveni
```

---

## 4. Datový model (telefon)

Model kopíruje data webu, aby se nic neztratilo při převodu tam a zpět.
Klíčem je **slug** z webu (`jan-novak-1849`); u nových osob vzniká v telefonu
dočasné ID `tmp-<uuid>` a server při přijetí změny přidělí slug.

| Tabulka | Hlavní pole |
|---|---|
| `person` | id (slug), jméno, příjmení, rodné příjmení, pohlaví, narození/úmrtí (datum jako text + strojově: rok, měsíc, den, přesnost „asi/před/po/mezi“), místo, stav doložení (`doc` / ústně / odhad), generace, vztah k probandovi, poznámka, `rev`, `updated_at`, `deleted` |
| `family` | id, partner1, partner2, sňatek (datum, místo), rozvod, poznámka, `rev` |
| `child_link` | family_id, person_id, pořadí, typ (vlastní/nemanželské/adopce) |
| `fact` | person_id, typ (povolání, bydliště, náboženství, …), datum, místo, text |
| `record` | matriční zápis: id, typ (N/O/Z), farnost, kniha, folio, archiv, portál, URL snímku, shrnutí, přepis, překlad |
| `record_link` | record_id ↔ person_id, role (dítě, otec, kmotr, …) |
| `media` | id, cesta k souboru v telefonu, `remote_key` v R2, sha256, popisek, datum/rok, je portrét, stav nahrání |
| `media_tag` | media_id, person_id, výřez obličeje (x, y, w, h v 0–1) |
| `place` | normalizovaný název, obec, okres, souřadnice |
| `pending_change` | fronta změn (outbox), viz 5.2 |
| `sync_state` | poslední verze dat ze serveru, čas |

**Data podle data 1:1 s webem:** `site.json` už obsahuje `facts`, `records`,
`father/mother`, `spouse`, `family` – import ze serveru je tedy přímočarý.
Strukturovaná data (datum, místo) se doplní na serveru při exportu, aby je aplikace
nemusela vyčítat z textu.

---

## 5. Synchronizace s webem

### 5.1 Princip: „offline-first“ + změnový log
- Telefon má vždy **úplnou kopii** dat. Čte se jen z lokální databáze.
- Každá úprava v telefonu = **změna** (operace), ne přepsání celého souboru.
- Server změny přijme do **D1** jako frontu ke schválení / sloučení.
- Projekt na Macu zůstává **zdrojem pravdy**: skript `tools/stahni_zmeny.py`
  změny stáhne, zapíše do `data/*.json` (do `data/zmeny_z_aplikace.json` + sloučení),
  pak běží obvyklé `export_web.py` a nasazení. Web i aplikace dostanou novou verzi.

Tím se nemusí přestavovat dnešní web ani nástroje – přibude jen API a jeden skript.
(Varianta B v kap. 9: později může být zdrojem pravdy přímo D1.)

### 5.2 Formát změny

```json
{
  "id": "c0a8…uuid",               // vytváří telefon, server je idempotentní
  "device": "pixel-8-ales",
  "author": "ales",
  "ts": "2026-11-14T15:02:11Z",
  "entity": "person",
  "entityId": "jan-novak-1849",
  "baseRev": 17,                   // verze, kterou telefon upravoval
  "op": "update",                  // create | update | delete | link | unlink
  "fields": { "deathDate": "3. 2. 1921", "deathPlace": "Kladno" }
}
```

### 5.3 Průběh
1. **Stažení:** `GET /api/v1/snapshot?since=<verze>` → buď úplný balík
   (JSON, gzip; dnes ~400 kB), nebo jen rozdíly od poslední verze.
2. **Odeslání:** `POST /api/v1/changes` (dávka z outboxu) → server vrátí pro
   každou změnu `accepted` / `conflict` / `rejected` + nové ID u vytvořených osob.
3. **Fotky:** `PUT /api/v1/media/{sha256}` přímo do R2 (velké soubory po částech),
   pak metadata jako běžná změna. Stažení fotek líně (náhled hned, originál na požádání),
   volba „stáhnout všechny fotky pro offline“.
4. Spouští se automaticky (při spuštění aplikace, po úpravě, 1× za 6 h na Wi-Fi)
   i ručně tlačítkem „Synchronizovat“.

### 5.4 Konflikty
- Porovnává se **po polích**: když telefon změnil `deathDate` a na webu se mezitím
  změnila `note`, sloučí se obojí automaticky.
- Stejné pole změněné na obou stranách → konflikt; aplikace ukáže obě hodnoty
  a uživatel vybere (nebo ponechá obě do poznámky).
- Smazání osoby, která má mezitím nové údaje → vždy konflikt, nikdy tiché smazání.

### 5.5 API (nové části Workeru)

| Metoda | Cesta | Popis |
|---|---|---|
| POST | `/api/v1/login` | jméno + heslo → přístupový token (15 min) + obnovovací token (90 dní) |
| POST | `/api/v1/refresh` | obnova tokenu |
| GET | `/api/v1/snapshot` | data pro offline (úplná nebo rozdílová) |
| POST | `/api/v1/changes` | dávka změn z telefonu |
| GET | `/api/v1/changes?status=pending` | pro `stahni_zmeny.py` (role admin) |
| POST | `/api/v1/changes/{id}/merged` | potvrzení sloučení z Macu |
| PUT/GET | `/api/v1/media/{sha256}` | nahrání / stažení fotky (R2) |
| GET | `/api/v1/gedcom` | úplný GEDCOM (stejný jako `/stazeni/`, ale s tokenem) |

---

## 6. Přihlášení, role, soukromí

- **Účty** v D1 (jméno, hash hesla PBKDF2/Argon2, role). Zakládá je admin
  skriptem, aby se nemusela řešit registrace.
- **Role:**
  - *čtenář* – stáhne data, prohlíží offline, nemůže odesílat změny;
  - *editor* – může navrhovat změny a nahrávat fotky;
  - *admin* – schvaluje a slučuje změny (to dělá skript na Macu).
- **Žijící osoby:** veřejný web skrývá přesná data žijících. Aplikace po přihlášení
  dostane **úplná data** (jako zaheslovaný GEDCOM). Bez přihlášení nic z webu nestáhne.
- Token v Android Keystore; data v telefonu volitelně za zámkem (biometrie / PIN).
- Hesla se nikam neukládají v čitelné podobě – ani do repozitáře, ani do aplikace.
- Zálohy Androidu (`allowBackup`) vypnuté pro databázi s citlivými daty.

---

## 7. GEDCOM

### Import
- Formáty: GEDCOM **5.5.1** a **7.0**, kódování UTF-8, UTF-16, ANSEL (starší programy),
  Windows-1250; automatická detekce podle `HEAD.CHAR` a BOM.
- Spojování řádků `CONC`/`CONT`, vlastní tagy (`_…`) se zachovají ve „surovém“
  poli, aby se při exportu neztratily.
- Podporované záznamy: `INDI`, `FAM`, `SOUR`, `REPO`, `NOTE`, `OBJE`, události
  (`BIRT`, `CHR`, `DEAT`, `BURI`, `MARR`, `DIV`, `OCCU`, `RESI`, `RELI` …).
- Česká data („9. 5. 1849“) i GEDCOM data (`9 MAY 1849`, `ABT`, `BEF`, `BET … AND …`).
- Import jako **nový strom**, nebo **sloučení** do existujícího (návrh dvojic podle
  jména, roku a místa narození; uživatel potvrdí).
- Import `.zip` / GEDZIP – i s fotkami.

### Export
- GEDCOM 5.5.1 (nejširší kompatibilita: MyHeritage, Ancestry, Gramps, FTB)
  nebo 7.0; volba „včetně žijících osob“ (výchozí: ne – jen roky).
- `.zip` = `rodokmen.ged` + složka `media/` s fotkami (odkazy `OBJE/FILE`).
- Kompatibilita s exportem webu: stejné ID (`@I…@` mapované na slug v `_UID`/`REFN`),
  aby se dal export z telefonu porovnat s `tools/ged_export.py`.

### Testy
Modul `:gedcom` má unit testy na: kulatý převod (import → export → import = totéž),
web GEDCOM, ukázkové soubory z Gramps / MyHeritage / FTB, poškozené soubory.

---

## 8. Fotografie

- Přidání z **galerie** (Android Photo Picker – bez oprávnění k celé galerii)
  nebo **fotoaparátem** (CameraX), i hromadně.
- Uložení do interního úložiště aplikace; pro zobrazení náhled 512 px,
  originál se drží v plné kvalitě. Odstranění GPS z EXIF před nahráním (volba).
- **Označení osob:** na fotce vyznačit obdélník a přiřadit osobu (jako GEDCOM
  `_FACE`/`OBJE` s výřezem); z výřezu jde vytvořit portrét osoby.
- Typy: portrét, skupinová fotka, **dokument** (matriční zápis, oddací list, parte)
  – dokument lze připojit i k matričnímu zápisu.
- Galerie u osoby + společná galerie s filtrem (osoba, rok, místo, typ).
- Na webu se fotky zobrazí po schválení (z R2 přes Worker, jen přihlášeným, pokud
  jde o žijící osoby).

---

## 9. Varianty propojení s webem

| | A – změny přes frontu (doporučeno) | B – databáze D1 jako zdroj pravdy |
|---|---|---|
| Co se mění na webu | přibude API + D1 (fronta) + R2 | data se přesunou z `data/*.json` do D1, web se generuje z D1 |
| Kontrola změn | každou změnu vidíte a schválíte na Macu | změna platí hned |
| Práce s Claude nad `data/*.json` | beze změny | nutno přepsat nástroje |
| Kdy se změna projeví na webu | po sloučení a nasazení | hned (dynamické stránky) |
| Náročnost | nízká | vysoká |

Doporučuji **A** – zachová dosavadní pečlivé ověřování proti matrikám a nic
nerozbije. Na B jde přejít později.

---

## 10. Obrazovky

1. **Úvod / přihlášení** – „Přihlásit k webu“ nebo „Pracovat offline / importovat GEDCOM“.
2. **Osoby** – hledání, filtry (linie, generace, žijící, s fotkou, bez zdroje),
   řazení. Odznak „neodesláno“ u upravených.
3. **Detail osoby** – portrét, životní data, fakta, rodina (klikací), matriční zápisy,
   fotky, poznámky; tlačítko Upravit.
4. **Editor osoby** – formulář s kontrolou dat (úmrtí před narozením, matka mladší
   12 let …), výběr místa z dříve použitých.
5. **Strom** – vývod (předci), rozrod (potomci), přesýpací hodiny; zoom, posun,
   klepnutí otevře detail.
6. **Matriční zápisy** – seznam, detail s přepisem a překladem, odkaz do e-badatelny.
7. **Fotky** – galerie, detail fotky s označenými osobami.
8. **Synchronizace** – stav, fronta neodeslaných změn, konflikty.
9. **Import / Export GEDCOM**.
10. **Nastavení** – účet, adresa webu, automatická synchronizace, jen Wi-Fi,
    zámek aplikace, motiv.

---

## 11. Postup (fáze)

| Fáze | Obsah | Výsledek |
|---|---|---|
| 0 | Kostra projektu, CI (GitHub Actions → APK), modul `:gedcom` s testy | build a import GEDCOM v testech |
| 1 | Room model, import GEDCOM ze souboru, seznam + detail osoby, hledání | aplikace jen offline z `.ged` |
| 2 | Strom (vývod/rozrod), matriční zápisy | prohlížeč rodokmenu |
| 3 | Editace offline, export GEDCOM / ZIP | samostatný nástroj bez webu |
| 4 | Fotky (přidání, ořez, označení osob, export) | |
| 5 | **Web:** API ve Workeru, D1, R2, účty; export `snapshot.json` z `export_web.py` | (práce v session webu) |
| 6 | Přihlášení, stažení dat, odesílání změn, nahrávání fotek; `tools/stahni_zmeny.py` | propojení s webem |
| 7 | Konflikty, ladění, ikona, podepsání, vydání (APK přímo / Google Play – interní testování) | verze 1.0 |

Fáze 1–4 nejsou na webu závislé – aplikace bude použitelná dřív, než bude hotové API.

---

## 12. Rozhodnutí (2026-11-14)

1. **Propojení: varianta A** – změny z telefonu jdou do fronty a schvalují se na Macu
   (`tools/stahni_zmeny.py`), teprve pak se web znovu nasadí.
2. **Uživatelé: i další členové rodiny** – víc účtů s rolemi čtenář / editor / admin.
   Každá změna nese autora, při schvalování je vidět, kdo ji navrhl.
3. **Distribuce: APK soubor** – ke stažení z webu za přihlášením; aplikace sama
   upozorní na novou verzi (`/api/v1/app-version`). Sestavení a podpis v GitHub Actions.
4. **Fotky i na veřejném webu** – schválené fotky se zobrazují všem. Výjimka:
   fotky, kde je označená žijící osoba, jen přihlášeným, pokud je admin při
   schválení výslovně neuvolní (stejné pravidlo jako u přesných dat žijících).
5. **Vlastní doména** – zatím zůstává `*.workers.dev` (zdarma). Doména jde
   kdykoli doplnit; v aplikaci bude adresa webu v nastavení, takže změna nic nerozbije.
