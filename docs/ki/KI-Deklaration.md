# KI-Deklaration

Gemäss «KI an der hftm einsetzen (Studierende)», Version 1.1 vom 02.02.2026.

## Eigenständigkeitserklärung

Diese Arbeit ist meine eigene Leistung. Fremde Quellen sind gekennzeichnet. Ich habe durchgehend steuernd gearbeitet
und allfällige von einer Künstlichen Intelligenz erzeugte Inhalte nicht unreflektiert übernommen. Alle verwendeten
Hilfsmittel, inkl. generativer KI, sind im Hilfsmittelverzeichnis deklariert.

Thema und fachliche Grundidee stammen von mir. Den Projektsteckbrief habe ich auf Basis meiner eigenen Skizze mit
KI-Unterstützung ausformuliert und die Vorschläge geprüft. KI-Vorschläge zu Code und Dokumentation habe ich geprüft,
angepasst und durch automatisierte Tests gegen PostgreSQL abgesichert.

Michael Gasser, _Ort, Datum_

## Hilfsmittelverzeichnis

| Hilfsmittel | Wozu eingesetzt? | Betroffene Stellen/Dateien | Version/Datum                 |
|-------------|------------------|----------------------------|-------------------------------|
| Claude (claude.ai, Anthropic) | Validierung und Ausformulierung des Projektsteckbriefs, Word-Layout | `docs/management/Projectsketch_RoomBook.pdf` | Claude Opus 5.5, 25.09.2026    |
| Claude Code (Anthropic) | Review des Projektgerüsts, Umsetzungsplanung, Code-Entwürfe, Fehleranalyse | GitHub-Repository, siehe Prompt-Verzeichnis | Claude Opus 5.5, ab 25.09.2026 |
| Spring Initializr | Erzeugung des Maven-Projektgerüsts | `pom.xml`, `mvnw*`, `src/` (Grundgerüst) | 25.09.2026                    |

## Prompt-Verzeichnis

Zusammengefasst pro Arbeitspaket. Die Prompts sind sinngemäss wiedergegeben.

| Nr. | Ziel des Prompts | Prompt | KI-Output genutzt? | Eigene Bearbeitung                                                                                                                                                                               | Betroffene Stelle |
|-----|------------------|--------|--------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------|
| 1 | Projektsteckbrief | Validiere und ergänze meine Skizze (Thema, Anforderungen, Konfliktfall, Belegungsrate) gemäss den Vorgaben und bereite einen Onepager vor … | teilweise | Thema und Grundanforderungen selbst vorgegeben, Vorschläge (Serienbuchung, Exclusion-Constraint, Hierarchie) geprüft und übernommen, technische Vorausplanung nicht in den Steckbrief übernommen | Projektsteckbrief |
| 2 | Prüfung Projektgerüst | Lies den Projektauftrag und den Steckbrief und prüfe, ob das Basis-Setup passt … | teilweise | Vorschläge geprüft, Umgebung eingerichtet und validiert, Commit selbst erstellt                                                                                                                  | `pom.xml`, `compose.yaml`, `application.yaml`, `README.md` (Commit 01474fa) |
| 3 | Umsetzungsplanung | Erstelle einen groben Plan für die Umsetzung … | teilweise | Offene Entscheide selbst festgelegt                                                                                                                                                              | `docs/PLAN.md`, `CLAUDE.md` |
| 4 | Vorlage KI-Deklaration | Bereite die KI-Dokumentation gemäss Richtlinie vor … | ja | Angaben geprüft und ergänzt                                                                                                                                                                      | `docs/ki/KI-Deklaration.md` |
| 5 | Entities und Baseline-Migration | Prüfe meine Entity-Klassen gegen Plan und Steckbrief, schlage Verbesserungen vor; erstelle einen ersten Entwurf der Baseline-Migration … | teilweise | Entities selbst entworfen, Review-Vorschläge geprüft und teilweise selbst umgesetzt, Längenbegrenzungen selbst eingebracht; V1-Entwurf geprüft und Schritt für Schritt erklären lassen, Benennung nach Unterrichtskonvention vorgegeben und Spaltennamen der Entities daran angepasst | `booking/`, `location/`, `room/`, `common/user/` (Commit 636cce4), `V1__init.sql`, `docs/PLAN.md` |
| 6 | Schema-Tests | Erstelle eine erste Version der Tests, die die DB-Regeln aus V1 nachweisen … | ja | Testfälle geprüft und ausgeführt, Zeitgenauigkeit der Buchungen hinterfragt und Rundung auf Minuten als Entscheid festgelegt | `SchemaConstraintsTest.java`, `docs/PLAN.md` |
| 7 | V2-Migration und Migrationstest | Prüfe meine V2-Migration und Entity-Anpassung; passe die Schema-Tests an und erstelle einen Test für den Übergang V1 → V2 … | teilweise | V2-Migration und Entity selbst erstellt, Review-Hinweise (Syntaxfehler, CHECK gegen leere Titel) selbst umgesetzt; Tests geprüft und ausgeführt, Klasse in `SchemaMigrationTest` umbenannt und Formatierung angepasst | `V2__booking_add_title.sql`, `Booking.java`, `SchemaConstraintsTest.java`, `SchemaMigrationTest.java` |
| 8 | ER-Diagramm | Dokumentiere das aktuelle Schema im README mittels Mermaid-ERD … | ja | Darstellung geprüft und Layout-Überarbeitung verlangt, Kardinalitäten hinterfragt und entschieden, die fachliche Sicht (Serie 1..n) mit Hinweis auf die Absicherung im Service beizubehalten | `README.md`, `docs/PLAN.md` |
| 9 | Paketstruktur, A1/A2 und Fehler-Mapping | Erstelle einen ersten Entwurf der Paketstruktur mit Controller, Service und Repository pro Entity; setze danach Auth, A1, A2 und das Fehler-Mapping um … | teilweise | Entwurf geprüft, Report-Paket auf später verschoben, Umsetzung und Tests geprüft und ausgeführt | `location/`, `room/`, `booking/`, `common/`, `LocationApiTest.java`, `RoomApiTest.java`, `pom.xml`, `docs/PLAN.md` |
| 10 | Buchungen (Phase 3) | Setze die nächsten Schritte für Phase 3 um: Einzel- und Serienbuchung, Ändern/Stornieren, Tests für Atomarität und Nebenläufigkeit … | teilweise | Umsetzung und Tests geprüft und ausgeführt | `booking/`, `BookingApiTest.java`, `BookingConcurrencyTest.java`, `docs/PLAN.md`, `README.md` |
| 11 | Dev-Seed-Daten | Wie lege ich App-User an? Setze einen Dev-Seed nur für den lokalen Start um … | ja | Variante aus drei Vorschlägen gewählt, lokalen Start geprüft | `application-dev.yaml`, `db/dev/R__dev_seed.sql`, `pom.xml`, `README.md` |
| 12 | Lesezugriffe (Phase 4) | Setze Phase 4 um: rekursive CTE, freie Räume, Buchungsliste mit Filter/Pagination, Abfragezählung für N+1, und gib eine Übersicht für das Review … | teilweise | Umsetzung, Tests und Messwerte geprüft | `location/`, `room/`, `booking/`, `RoomSearchTest.java`, `BookingSearchTest.java`, `BookingQueryCountTest.java`, `pom.xml`, `README.md`, `docs/PLAN.md` |
| 13 | Reporting (Phase 5) | Was ist beim Reporting zu beachten? Setze Phase 5 mit den Vorschlägen um … | teilweise | Stolperstellen und Entscheide besprochen und bestätigt (Nenner, Zeitraum als Datum, Feiertage), Umsetzung und Testwerte geprüft | `V3__view_active_booking.sql`, `report/`, `ReportApiTest.java`, `README.md`, `docs/PLAN.md` |
| 14 | Testdaten (Phase 6, T9) | Wie gehe ich Phase 6 an? Ist der Generator nicht zu aufwendig? Setze ihn in der vereinfachten Form um … | ja | Vorgehen hinterfragt und Vereinfachung verlangt, Umsetzung und Verteilung geprüft | `scripts/generate-data.sql`, `README.md`, `docs/PLAN.md` |
