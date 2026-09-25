# RoomBook

HFTM-Projektarbeit (Database Development): Raumreservationssystem mit Spring Boot 4, Java 25, PostgreSQL 18.

- Auftrag und technische Pflichtanforderungen T1–T12: `docs/management/ProjectOrder.pdf`
- Fachliche Anforderungen A1–A7 und Modellskizze: `docs/management/Projectsketch_RoomBook.pdf`
- Umsetzungsplan und Entscheide: `docs/PLAN.md` – vor jeder Arbeit lesen, nach jeder Arbeit abhaken bzw. ergänzen

## Befehle

- Tests: `./mvnw verify` (Docker Desktop muss laufen, Testcontainers)
- Start: `./mvnw spring-boot:run` (startet PostgreSQL über `compose.yaml`)

## Regeln

- Schema nur über Flyway-Migrationen; angewendete Migrationen nie ändern, immer neue Version anlegen.
- Geschäftsregeln zusätzlich in der DB absichern (Constraints/Trigger), nicht nur in der API.
- DTOs (Records) getrennt von Entities, Geschäftslogik im Service, nicht im Controller.
- Jede Anforderung braucht einen Nachweis (Test, `.http`-Aufruf oder Messung) und einen Eintrag in der README-Nachweistabelle.
- KI-Einsatz wird in `docs/ki/KI-Deklaration.md` dokumentiert: pro abgeschlossenem Arbeitspaket eine Zeile im
  Prompt-Verzeichnis (Ziel, sinngemässer Prompt, Nutzung, eigene Bearbeitung, Dateien/Commit), keine Einzelprompts.
