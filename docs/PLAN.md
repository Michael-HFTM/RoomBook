# Umsetzungsplan RoomBook

Grober Fahrplan von der leeren Basis bis zur Abgabe. Quellen: `docs/management/ProjectOrder.pdf` (Auftrag, T1–T12)
und `docs/management/Projectsketch_RoomBook.pdf` (Steckbrief, A1–A7). Erledigtes abhaken, Entscheide unten nachtragen.

## Phasen

### 1. Schema und Migrationen (T1, T2)
- [x] `V1__init.sql`: `btree_gist`, Tabellen `location` (selbstreferenzierend), `room`, `app_user`, `booking_series`, `booking`
  - CHECK `start_time < end_time`, CHECK `capacity > 0`, UNIQUE `(location_id, name)`, CHECK auf Enum-Werte (`status`, `type`, `role`)
  - Exclusion-Constraint: `EXCLUDE USING gist (room_id WITH =, tstzrange(start_time, end_time) WITH &&) WHERE (status <> 'CANCELLED')`
  - Zyklenschutz der Hierarchie als DB-Trigger (Regel nicht nur in der API)
- [x] `SchemaConstraintsTest`: DB-Regeln aus V1 direkt per SQL geprüft (Exclusion, CHECKs, UNIQUE, Zyklen-Trigger)
- [x] `V2__booking_add_title.sql`: `booking.title` mit Backfill aus Raumname, danach `NOT NULL` und CHECK gegen leere Titel
- [x] Test: Neuaufbau ab leerer DB (Spring-Kontext) und Übergang V1 → V2 mit Bestandsdaten (`SchemaMigrationTest`, Flyway `target`)
- [x] ER-Diagramm (Mermaid im README) und Begründung der Schemaentscheide

### 2. Entities und CRUD (T3, A1, A2)
- [x] Paketstruktur pro Fachbereich: `location`, `room`, `booking`, `common`; je Controller → Service → Repository, DTOs als Records
  (`report` folgt in Phase 5, `booking` vorerst nur Gerüst)
- [x] Gemockte Auth: Header `X-User-Id`, Rolle aus `app_user`; fehlend/unbekannt → 401
- [x] A1 Standorte erfassen/ändern/deaktivieren, A2 Räume erfassen/ändern/deaktivieren (`LocationApiTest`, `RoomApiTest`)
- [x] Zentrales Fehler-Mapping (`@RestControllerAdvice`, `ProblemDetail`): 400, 403, 404, 409 (SQLState `23P01`, Optimistic Lock)
  - 409 für `23P01` und Optimistic Lock: `BookingApiTest`, `BookingConcurrencyTest`

### 3. Buchungen (A4, A5, T4, T5)
- [x] A4 Einzel- und Serienbuchung (wöchentlich, max. 12) in einer `@Transactional`-Methode, Termine einzeln flushen
- [x] Start- und Endzeit im Service auf volle Minuten runden (siehe Entscheid 2026-09-30)
- [x] T4-Test: Serie, deren n-ter Termin mit bestehender Buchung kollidiert → Serie und frühere Termine sind nicht in der DB
- [x] A5 Ändern/Stornieren nur Buchender oder Admin (sonst 403), Storno = Status `CANCELLED`; `@Version` → 409
- [x] T5-Tests mit zwei Threads (`CountDownLatch`): gleichzeitige überlappende Buchung (Exclusion → genau eine gewinnt) und gleichzeitige Änderung (Optimistic Lock)

### 4. Lesezugriffe (A3, A6, T6, T7, T11)
- [x] Rekursive CTE für den Teilbaum einer Gruppierung (A3, A6, A7)
- [x] A3 Freie Räume: aktiv, Kapazität ≥ x, keine überlappende aktive Buchung
- [x] A6 Buchungsliste: DB-seitig filtern (Raum, Benutzer, Gruppierung, Zeitraum, Status), Sortierung `start_time, id`, Pagination (T7)
- [x] JPQL-DTO-Projektion für A6 (T6)
- [x] T11: Abfragezählung mit datasource-proxy für 1/10/100 Treffer, N+1 belegen bzw. ausschliessen, Vorher/Nachher

### 5. Auswertungen (A7, T6)
- [x] View `v_active_booking` (nicht storniert, mit Raum und Gruppierung)
- [x] A7 Belegungsrate über JDBC (`JdbcClient`, parametrisiert): Buchungen auf Mo–Fr 07:00–18:00 (Europe/Zurich) kappen, JOIN und Aggregation
- [x] Test mit bekannten Ergebnissen, inkl. mehrerer Buchungen pro Raum und Buchungen über die Geschäftszeit hinaus

### 6. Testdaten und Performance (T9, T10)
- [x] Generator `scripts/generate-data.sql` (deterministisch mit `setseed`): ≥ 100'000 Buchungen, überschneidungsfrei
  - Ziel aus Steckbrief-Vorarbeit: ~150'000 Buchungen, ~200 Räume, 2 Jahre, 500 Benutzer, ungleich verteilt (beliebte Räume, Stosszeiten), ~10 % Stornos
- [x] T10: Abfrage wählen (voraussichtlich A6 nach Benutzer und Zeitraum), `EXPLAIN (ANALYZE, BUFFERS)` vorher/nachher, mehrfach messen
- [x] Index als eigene Migration, damit der Ausgangszustand reproduzierbar bleibt; Nutzen und Kosten dokumentieren
- [x] Messungen unter `docs/performance/`

### 7. Cache (T12)
- [ ] Kandidat: Teilbaum der Standorthierarchie (ändert selten; `LocationService#findSubtreeIds`, genutzt von A6;
  A3 und A7 lösen den Teilbaum in ihrer eigenen SQL-Abfrage auf)
- [ ] `@EnableCaching`, Schlüssel = Location-ID, Eviction bei jeder Änderung in A1
- [ ] Test: erster Zugriff (Query), Treffer (keine Query), nach Änderung (neue Query, aktuelles Ergebnis)

### 8. Abgabe
- [ ] API-Beispielaufrufe als `http/*.http`
- [ ] README: Nachweistabelle A1–A7 und T1–T12 vollständig, Entscheide, Einschränkungen
- [ ] `docs/ki/KI-Deklaration.md` vervollständigen (laufend pro Arbeitspaket nachgeführt), Ort/Datum ergänzen, im README verlinken
- [ ] Git-Tag für den Abgabestand, im README vermerken; Dozent (simon.erhardt@hftm.ch) einladen

## Entscheide

Hier Entscheide mit Datum und kurzer Begründung festhalten.

- 2026-09-25: PostgreSQL 18, Flyway als einzige Schemaquelle (`ddl-auto: validate`), `open-in-view: false`.
- 2026-09-30: Enums als `varchar` mit CHECK: `LocationType` (BRANCH, BUILDING, FLOOR), `Role` (USER, ADMIN),
  `RecurrenceRule` (nur WEEKLY, A4 verlangt nichts anderes; kein RRULE-Parsing). Deaktivieren statt Löschen für
  Standorte und Räume; stornierte Buchungen sind nicht mehr verschiebbar.
- 2026-09-30: V1: IDs als `GENERATED ALWAYS AS IDENTITY`. Zyklen-Trigger nur auf `UPDATE OF parent_location_id`, weil ein neuer
  Knoten noch nicht Vorfahre sein kann; ein Advisory-Lock serialisiert parallele Umhängungen. Kein Index auf
  `booking(app_user_id, start_time)` in V1, er kommt als eigene Migration für die T10-Messung.
- 2026-09-30: Benennung nach Unterrichtskonvention: Tabellen Singular/snake_case, PK-Spalte `<tabelle>_id`, FK-Spalte
  `<reftabelle>_id` (Selbstreferenz `parent_location_id`); Constraints `pk_<tabelle>`, `fk_<tabelle>_<reftabelle>`,
  `uq_/ck_<tabelle>_<spalte>`, `ex_` für Exclusion, Indizes `idx_<tabelle>_<spalte>`.
- 2026-09-30: Zeitgenauigkeit von Buchungen: `timestamptz` speichert auf die Mikrosekunde. Start und Ende werden später
  im Service auf volle Minuten gerundet; vorerst ohne DB-Constraint, da der Steckbrief kein Raster vorgibt.
- 2026-09-30: Phase 2: Lesende Endpunkte (`GET`) ohne `X-User-Id`, schreibende verlangen den Header (fehlend/unbekannt
  → 401, keine Admin-Rolle → 403). Deaktivieren über `DELETE` (Soft Delete, setzt `active = false`). Deaktivieren wirkt
  nur auf den Knoten selbst; ob Räume unter deaktivierten Standorten buchbar sind, wird mit A3/A4 entschieden.
  Services flushen nach Schreiboperationen, damit DB-Verletzungen (Trigger, UNIQUE) in der Transaktion als
  HTTP-Fehler ankommen. Constraint-Name aus `PSQLException` (PostgreSQL-Treiber daher im Compile-Scope), weil
  Hibernate den Namen bei Trigger-Fehlern nicht erkennt. CHECK/NOT NULL → 400, UNIQUE/Exclusion/FK → 409.
- 2026-09-30: Phase 3: Sekunden werden abgeschnitten (`truncatedTo(MINUTES)`), nicht kaufmännisch gerundet.
  Serientermine werden in `Europe/Zurich` berechnet, damit sie über die Zeitumstellung dieselbe Ortszeit behalten.
  Überschneidungen prüft nur das Exclusion-Constraint (keine Vorabprüfung im Service), weil nur es auch parallele
  Anfragen abdeckt. Änderungen verlangen die gelesene `version` im Request, damit verlorene Updates auch über
  getrennte HTTP-Anfragen erkannt werden; `@Version` sichert den gleichzeitigen Fall ab. Buchbar ist ein Raum, wenn
  er selbst aktiv ist; deaktivierte übergeordnete Standorte werden mit der rekursiven CTE (Phase 4) berücksichtigt.
- 2026-09-30: Dev-Seed: Profil `dev` (aktiv bei `spring-boot:run`) ergänzt Flyway um `db/dev` mit einer idempotenten
  Repeatable-Migration (Benutzer, kleine Standorthierarchie, Räume). Nicht in `db/migration`, weil Seed-Daten nicht
  zum Schema gehören und mit den Testdaten kollidieren würden.
- 2026-09-30: Phase 4: Ein deaktivierter Standort blendet seinen ganzen Teilbaum aus: Räume darunter sind weder frei
  (A3) noch buchbar (A4). Die Buchungsliste (A6) zeigt dagegen auch Buchungen unter deaktivierten Standorten
  (Historie). Drei rekursive Abfragen: Teilbaum-IDs (A6, später A7 und Cache T12), Vorfahren aktiv (A4) und für A3
  eine CTE ab den Wurzeln nur über aktive Knoten. A6 als statische JPQL-Abfrage mit `:param IS NULL OR …` und fester
  Sortierung `start_time, id`; Seitengrösse max. 100. `datasource-proxy` nur noch im Test-Scope.
- 2026-09-30: Phase 5 (A7): Zeitraum als `LocalDate` (Ortszeit Zürich, `to` inklusive). Geschäftszeitfenster pro
  Werktag via `generate_series` in Ortszeit gebildet und nach `timestamptz` umgerechnet (Zeitumstellung korrekt);
  Kappung über den Schnitt `*` von `tstzrange`. Gebuchte Stunden werden pro Raum aggregiert, bevor sie an die Räume
  gejoint werden, damit sich der Nenner nicht vervielfacht. Es zählen alle Räume im Teilbaum, auch deaktivierte, weil
  das Schema keinen Deaktivierungszeitpunkt kennt. Feiertage werden ignoriert. Ranking über die Sortierung nach
  Belegungsrate; Auswertungen nach Wochentag/Tageszeit und Stornoquote aus dem Steckbrief bewusst nicht umgesetzt.
  Gesamtrate eines Standorts = Summe gebuchte / Summe verfügbare Stunden seiner Räume.
- 2026-09-30: Phase 6 (T9): Generator über ein Stundenraster pro Raum und Werktag (07–18 Uhr Zürich), Buchung
  30/45/60 min ab Slotbeginn, damit Überschneidungen ausgeschlossen sind. Schiefe nur bei Raumbeliebtheit und
  Buchungen pro Benutzer (nötig für A7-Ranking und T10); Stosszeiten und Serien bewusst weggelassen. Läuft per
  `psql` gegen die migrierte DB, nicht über Flyway, weil Testdaten nicht zum Schema gehören.
- 2026-09-30: Phase 6 (T10): Gemessen wird das SQL aus dem Hibernate-Log als `PREPARE`, in den Modi custom, generic
  und auto, weil der JDBC-Treiber ab der 5. Ausführung serverseitige Prepared Statements nutzt. Ohne Index wechselte
  PostgreSQL beim Vielbucher auf den generischen Plan (`? IS NULL OR …` nicht auflösbar). V4 `(app_user_id,
  start_time)`: Liste 12.3 → 1.3 ms, auto bleibt danach bei custom. Deshalb kein `plan_cache_mode` und keine
  dynamische Abfrage. Kosten: 5 MB, Masseneinfügen ca. 6 % langsamer.
