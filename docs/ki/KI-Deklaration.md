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
