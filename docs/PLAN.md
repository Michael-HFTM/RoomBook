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
- [ ] Paketstruktur pro Fachbereich: `location`, `room`, `booking`, `report`, `common`; je Controller → Service → Repository, DTOs als Records
- [ ] Gemockte Auth: Header `X-User-Id`, Rolle aus `app_user`; fehlend/unbekannt → 401
- [ ] A1 Standorte erfassen/ändern/deaktivieren, A2 Räume erfassen/ändern/deaktivieren
- [ ] Zentrales Fehler-Mapping (`@RestControllerAdvice`, `ProblemDetail`): 400, 403, 404, 409 (SQLState `23P01`, Optimistic Lock)

### 3. Buchungen (A4, A5, T4, T5)
- [ ] A4 Einzel- und Serienbuchung (wöchentlich, max. 12) in einer `@Transactional`-Methode, Termine einzeln flushen
- [ ] Start- und Endzeit im Service auf volle Minuten runden (siehe Entscheid 2026-09-30)
- [ ] T4-Test: Serie, deren n-ter Termin mit bestehender Buchung kollidiert → Serie und frühere Termine sind nicht in der DB
- [ ] A5 Ändern/Stornieren nur Buchender oder Admin (sonst 403), Storno = Status `CANCELLED`; `@Version` → 409
- [ ] T5-Tests mit zwei Threads (`CountDownLatch`): gleichzeitige überlappende Buchung (Exclusion → genau eine gewinnt) und gleichzeitige Änderung (Optimistic Lock)

### 4. Lesezugriffe (A3, A6, T6, T7, T11)
- [ ] Rekursive CTE für den Teilbaum einer Gruppierung (A3, A6, A7)
- [ ] A3 Freie Räume: aktiv, Kapazität ≥ x, keine überlappende aktive Buchung
- [ ] A6 Buchungsliste: DB-seitig filtern (Raum, Benutzer, Gruppierung, Zeitraum, Status), Sortierung `start_time, id`, Pagination (T7)
- [ ] JPQL-DTO-Projektion für A6 (T6)
- [ ] T11: Abfragezählung mit datasource-proxy für 1/10/100 Treffer, N+1 belegen bzw. ausschliessen, Vorher/Nachher

### 5. Auswertungen (A7, T6)
- [ ] View `v_active_booking` (nicht storniert, mit Raum und Gruppierung)
- [ ] A7 Belegungsrate über JDBC (`JdbcClient`, parametrisiert): Buchungen auf Mo–Fr 07:00–18:00 (Europe/Zurich) kappen, JOIN und Aggregation
- [ ] Test mit bekannten Ergebnissen, inkl. mehrerer Buchungen pro Raum und Buchungen über die Geschäftszeit hinaus

### 6. Testdaten und Performance (T9, T10)
- [ ] Generator `scripts/generate-data.sql` (deterministisch mit `setseed`): ≥ 100'000 Buchungen, überschneidungsfrei
  - Ziel aus Steckbrief-Vorarbeit: ~150'000 Buchungen, ~200 Räume, 2 Jahre, 500 Benutzer, ungleich verteilt (beliebte Räume, Stosszeiten), ~10 % Stornos
- [ ] T10: Abfrage wählen (voraussichtlich A6 nach Benutzer und Zeitraum), `EXPLAIN (ANALYZE, BUFFERS)` vorher/nachher, mehrfach messen
- [ ] Index als eigene Migration, damit der Ausgangszustand reproduzierbar bleibt; Nutzen und Kosten dokumentieren
- [ ] Messungen unter `docs/performance/`

### 7. Cache (T12)
- [ ] Kandidat: Teilbaum der Standorthierarchie (ändert selten, wird von A3/A6/A7 oft gebraucht)
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
