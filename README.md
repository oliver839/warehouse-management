# Warehouse Management System

Eine Spring-Boot-Webanwendung zur Verwaltung von Lagerstandorten, Inventar und projektbezogenen Ressourcenreservierungen. Das Dashboard wird direkt von Spring Boot ausgeliefert und bietet einen responsiven Light-/Dark-Mode.

## Funktionen

- Lagerstandorte mit maximalem Volumen verwalten
- Inventar mit Menge, Platzbedarf und Lagerzuweisung einbuchen
- Volumenkapazität vor dem Speichern eines Items prüfen
- Inventar nach Lager filtern
- Aufträge mit Ressourcenbedarf anlegen
- Ressourcen für Aufträge reservieren, freigeben und verbrauchen
- Verfügbare und gesamte Bestandsmenge direkt im Dashboard anzeigen
- Persistenter Light-/Dark-Mode über Bootstrap 5 und `localStorage`

## Technologie

- Java 17
- Spring Boot 4
- Spring Data JPA / Hibernate
- H2 In-Memory-Datenbank
- Gradle Wrapper
- Bootstrap 5, Bootstrap Icons und Vanilla JavaScript

## Lokal starten

Voraussetzung: Java 17 ist installiert.

```bash
./gradlew bootRun
```

Unter Windows:

```bat
gradlew.bat bootRun
```

Danach ist das Dashboard unter [http://localhost:8080](http://localhost:8080) erreichbar. Die H2-Datenbank läuft im Speicher; beim Neustart beginnt sie wieder mit den Initialdaten.

## Testdaten

Beim Start legt `DataInitializer` automatisch folgende Daten an, sofern noch keine Lager existieren:

- Hauptlager München, maximales Volumen: `100.0 m³`
- Nebenlager Berlin, maximales Volumen: `50.0 m³`
- Akkubohrer: `15` Stück, `0.5 m³` pro Stück, im Hauptlager
- Schrauben M5: `5000` Stück, `0.001 m³` pro Stück, im Nebenlager

## Reservierungsablauf

Ein Auftrag startet mit dem Status `PENDING`. Erst bei Genehmigung wird Bestand reserviert.

| Statuswechsel | Wirkung auf das Inventar |
| --- | --- |
| `PENDING` → `APPROVED` oder `IN_PROGRESS` | Die angeforderten Mengen werden reserviert. |
| `APPROVED` / `IN_PROGRESS` → `REJECTED` | Reservierungen werden wieder freigegeben. |
| `APPROVED` / `IN_PROGRESS` → `COMPLETED` | Bestand und Reservierung werden jeweils um die zugewiesene Menge reduziert. |

Beispiel: Bei `100` Schrauben wird ein Auftrag mit `20` Schrauben zunächst nur angelegt. Nach **Genehmigen** zeigt das Dashboard `80 / 100`; nach **Abschließen** `80 / 80`.

## REST-API

| Ressource | Endpunkte |
| --- | --- |
| Warehouses | `GET`, `POST /api/warehouses`; `PUT`, `DELETE /api/warehouses/{id}`; `GET /api/warehouses/{id}/items` |
| Items | `GET`, `POST /api/items`; `PUT`, `DELETE /api/items/{id}` |
| Projects | `GET`, `POST /api/projects`; `DELETE /api/projects/{id}` |
| Projektressourcen | `POST /api/projects/{id}/allocations` |
| Projektstatus | `PATCH /api/projects/{id}/status?newStatus=APPROVED` |
| Zuweisungen | `GET`, `POST /api/allocations`; `DELETE /api/allocations/{id}` |

Ein Item wird als JSON mit Lagerreferenz angelegt, zum Beispiel:

```json
{
  "name": "Schrauben M5",
  "quantityInStock": 100,
  "spacePerUnit": 0.001,
  "warehouseId": 1
}
```

## Tests

```bash
./gradlew test
```

Neben dem Spring-Kontexttest prüft `ProjectServiceTests` den vollständigen Reservierungsablauf: Reservieren von `20` Einheiten und anschließendes Verbrauchsbuchen.

## Projektstruktur

```text
src/main/java/com/logistics/warehouse_management/
├── config/        Initiale Testdaten
├── controller/    REST-API
├── model/         JPA-Entitäten und Enums
├── repository/    Spring-Data-Repositories
└── service/       Kapazitäts- und Reservierungslogik

src/main/resources/static/
├── index.html     Dashboard
├── css/styles.css Design und Themes
└── js/app.js      UI- und API-Logik
```
