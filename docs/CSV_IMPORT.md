# CSV-Import

Die Anwendung verarbeitet jeden Upload atomar: Sobald eine Pflichtspalte oder eine Datenzeile fehlerhaft ist, wird der gesamte Import zurueckgerollt. Die Antwort enthaelt die betroffenen Zeilennummern und die Fehlerursachen.

## Endpunkte

- `POST /api/import/items` mit Multipart-Feld `file`
- `POST /api/import/projects` mit Multipart-Feld `file`
- `POST /api/import/project-lines` mit Multipart-Feld `file`

## Reihenfolge

1. Artikelstamm importieren
2. Auftraege importieren
3. Auftragspositionen importieren

## Spalten

Artikel: `sku,barcode,name,quantityInStock,spacePerUnit,warehouseId,type`

Auftraege: `orderNumber,name,description,customerName,customerEmail,deliveryAddress`

Auftragspositionen: `orderNumber,sku,quantity`

`sku`, `name`, `quantityInStock`, `spacePerUnit`, `warehouseId` und `type` sind beim Artikelimport erforderlich. `barcode` ist optional. Bei Auftraegen sind `orderNumber` und `name` erforderlich. Auftragspositionen benoetigen alle drei Spalten.

Beispieldateien liegen in diesem Ordner: `import-items.csv`, `import-projects.csv` und `import-project-lines.csv`.

## Packen und Bestand

Der Bestand wird beim Bestaetigen des Packvorgangs abgebucht. Das Picken veraendert nur Pickmengen und Reservierungen. Ein Pickauftrag kann nur einmal gepackt werden; die Lieferschein-ID wird als Referenz der `SHIPMENT`-Bestandstransaktion gespeichert.
