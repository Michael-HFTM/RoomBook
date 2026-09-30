# Performanceanalyse (T10)

## Abfrage

A6 Buchungsliste gefiltert nach Benutzer und Zeitraum (`BookingRepository#search`), erste Seite mit 20 Einträgen,
plus die Count-Abfrage der Pagination. Das SQL stammt unverändert aus dem Hibernate-Log (`logging.level.org.hibernate.SQL=debug`)
und ist in [`scripts/measure-booking-search.sql`](../../scripts/measure-booking-search.sql) als `PREPARE` hinterlegt.

Parameter: `userId` = 1 (Vielbucher, 7'810 Buchungen) bzw. 496 (Wenigbucher, 148 Buchungen),
`from` = 2026-03-01, `to` = 2026-04-01 (Europe/Zurich), übrige Filter leer.

## Umgebung

- PostgreSQL 18.6 (Docker-Image `postgres:18`, Standardkonfiguration, `shared_buffers` 128 MB)
- Docker Desktop (WSL2), Windows 11, AMD Ryzen 7 3700X, 16 GB RAM
- Daten aus [`scripts/generate-data.sql`](../../scripts/generate-data.sql): 171'783 Buchungen, Tabelle `booking` 17 MB
  (passt vollständig in `shared_buffers`, gemessen wird also mit warmem Cache)

## Messverfahren

```bash
docker compose exec -T postgres psql -U roombook -d roombook -v user_id=1 < scripts/measure-booking-search.sql
```

Pro Plan-Modus ein Plan mit `EXPLAIN (ANALYZE, BUFFERS)`, danach ein Aufwärmlauf und 10 gemessene Läufe
(`Execution Time`, Median). Gemessen wird direkt in der DB, ohne Anwendung und damit ohne Anwendungscache (T12).

- `force_custom_plan`: Parameter sind beim Planen bekannt, `? IS NULL OR …` fällt weg.
- `force_generic_plan`: Plan ohne Parameterwerte. Das bekommt die Anwendung, sobald der JDBC-Treiber ein
  serverseitiges Prepared Statement verwendet (ab der 5. Ausführung) und PostgreSQL den generischen Plan wählt.
- `auto` (Standard): welchen Plan PostgreSQL nach 10 Ausführungen tatsächlich nimmt (`pg_prepared_statements`).

## Ausgangszustand (V3, ohne Index)

Rohdaten: [`v3-user1.txt`](v3-user1.txt), [`v3-user496.txt`](v3-user496.txt)

| Benutzer | Abfrage | custom (Median) | generic (Median) | auto: Plan nach 10 Ausführungen |
|----------|---------|----------------:|-----------------:|---------------------------------|
| 1        | Liste   | 12.3 ms         | 16.4 ms          | generisch (6 generic / 5 custom) |
| 1        | Count   | 12.4 ms         | 14.6 ms          |                                 |
| 496      | Liste   | 12.6 ms         | 16.5 ms          | custom (0 generic / 11 custom)  |
| 496      | Count   | 12.4 ms         | 14.9 ms          |                                 |

Beide Abfragen lesen in jedem Fall die ganze Tabelle (`Parallel Seq Scan on booking`, 2'121 Seiten) und verwerfen
über 99 % der Zeilen. Die Laufzeit hängt deshalb kaum vom Benutzer ab.

- **custom:** Der Filter `app_user_id = 1` wird über die Join-Bedingung auf `booking` übertragen, es fehlt aber ein
  passender Index.
- **generic:** Der Filter bleibt als `$2 IS NULL OR u1_0.app_user_id = $2` auf `app_user` stehen. `booking` wird nur
  nach Zeitraum gefiltert (7'219 statt 316 Zeilen), der Benutzer erst im Join aussortiert.
- **auto:** Beim Vielbucher schätzt PostgreSQL den generischen Plan (1 Zeile geschätzt) günstiger als die custom
  Pläne und wechselt nach 5 Ausführungen. Die Anwendung bekommt dann den schlechteren Plan.

## Optimierung: Index (V4)

[`V4__booking_index_user_start_time.sql`](../../src/main/resources/db/migration/V4__booking_index_user_start_time.sql):
`CREATE INDEX idx_booking_app_user_id_start_time ON booking (app_user_id, start_time)`

Fachliche Begründung: „Meine Buchungen im Zeitraum X“ ist die häufigste Listenabfrage. `app_user_id` zuerst,
weil darauf mit Gleichheit gefiltert wird; `start_time` danach für die Zeitraumgrenze und die Sortierung der Liste.

Rohdaten: [`v4-user1.txt`](v4-user1.txt), [`v4-user496.txt`](v4-user496.txt)

| Benutzer | Abfrage | custom V3 → V4    | generic V3 → V4   | auto: Plan nach 10 Ausführungen |
|----------|---------|------------------:|------------------:|---------------------------------|
| 1        | Liste   | 12.3 → **1.26 ms** | 16.4 → 5.99 ms   | custom (0 generic / 11 custom)  |
| 1        | Count   | 12.4 → **1.25 ms** | 14.6 → 6.27 ms   |                                 |
| 496      | Liste   | 12.6 → **0.07 ms** | 16.5 → 0.19 ms   | custom (0 generic / 11 custom)  |
| 496      | Count   | 12.4 → **0.10 ms** | 14.9 → 0.18 ms   |                                 |

- **custom (Liste):** `Index Scan using idx_booking_app_user_id_start_time` liefert die Buchungen des Benutzers
  bereits nach `start_time` sortiert. Der Scan bricht nach 20 Zeilen ab, eine Sortierung entfällt.
- **custom (Count):** `Bitmap Index Scan` mit Benutzer und Zeitraum als Indexbedingung, nur 316 Zeilen werden gelesen.
- **generic:** Der Index wird über die Join-Bedingung (`app_user_id = u1_0.app_user_id`) genutzt, grenzt aber nur
  nach Benutzer ein. Der Zeitraum bleibt Filter, beim Vielbucher werden alle 7'810 Buchungen gelesen und 7'494 verworfen.
- **auto:** Mit dem Index sind die custom Pläne so günstig, dass PostgreSQL auch beim Vielbucher nicht mehr auf den
  generischen Plan wechselt. Die Anwendung bekommt damit den schnellen Plan.

Entscheid: Keine Änderung an `plan_cache_mode` und keine dynamische Abfrage. Der Wechsel auf den generischen Plan
war im Ausgangszustand ein Risiko, tritt mit dem Index nicht mehr auf, und selbst der generische Plan ist mit Index
schneller als jeder Plan ohne. Beobachten, falls neue Filterkombinationen dazukommen.

### Kosten

| Mass                                         | ohne Index | mit Index |
|----------------------------------------------|-----------:|----------:|
| Grösse (Tabelle `booking` 17 MB)             | –          | 5.0 MB    |
| Generator, 3 Läufe (171'783 Inserts)         | 9.01–9.10 s | 9.52–10.14 s |

- Speicher: +5 MB, knapp ein Drittel der Tabelle.
- Schreiben: jedes `INSERT` und jedes `UPDATE` von `app_user_id`/`start_time` pflegt den Index mit. Beim
  Masseneinfügen ca. 6 % langsamer (Median 9.06 → 9.61 s). Bei einzelnen Buchungen über die API fällt das
  gegenüber Netzwerk und Transaktion kaum ins Gewicht (nicht separat gemessen).
- Kein Nutzen für Abfragen ohne `userId` (z. B. nur Raum oder nur Zeitraum).

## Ausgangszustand wiederherstellen

Frische DB nur bis V3 migrieren, Testdaten laden, messen:

```bash
docker compose down -v
./mvnw spring-boot:run -Dspring-boot.run.arguments=--spring.flyway.target=3   # nach dem Start beenden
docker compose up -d
docker compose exec -T postgres psql -U roombook -d roombook -v ON_ERROR_STOP=1 < scripts/generate-data.sql
docker compose exec -T postgres psql -U roombook -d roombook -v user_id=1 < scripts/measure-booking-search.sql
```
