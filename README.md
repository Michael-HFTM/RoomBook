# RoomBook – Raumreservationssystem

HFTM Database Development Projektarbeit · Michael Gasser

Spring Boot und PostgreSQL. Die REST-API bucht Arbeits- und Meetingräume über mehrere Standorte hinweg,
schliesst Doppelbuchungen aus und wertet die Belegung aus.
Projektsteckbrief: [docs/management/Projectsketch_RoomBook.pdf](docs/management/Projectsketch_RoomBook.pdf)

## Fachliche Anforderungen

| Nr. | Anforderung                   |
|-----|-------------------------------|
| A1  | Standorthierarchie verwalten  |
| A2  | Räume verwalten               |
| A3  | Freie Räume suchen            |
| A4  | Buchung / Serienbuchung       |
| A5  | Buchung ändern / stornieren   |
| A6  | Buchungen einsehen            |
| A7  | Belegungsrate auswerten       |

## Voraussetzungen

- JDK 25
- Docker (für PostgreSQL via Docker Compose und Testcontainers)

## Start

```bash
./mvnw spring-boot:run     # startet PostgreSQL automatisch über compose.yaml
```

Swagger UI: http://localhost:8080/swagger-ui.html

## Tests

```bash
./mvnw verify              # Integrationstests gegen PostgreSQL (Testcontainers)
```

## Nachweise

| Anforderung | Umsetzung | Nachweis |
|-------------|-----------|----------|
| A1–A7       | TODO      | TODO     |
| T1–T12      | TODO      | TODO     |

## KI und Hilfsmittel

TODO: Eigenständigkeitserklärung, Hilfsmittelverzeichnis und Prompt-Verzeichnis gemäss HFTM-KI-Richtlinie 1.1.
