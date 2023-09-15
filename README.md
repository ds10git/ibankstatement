hibiscus.ibankstatement
=======================

Ein Plugin zum halbautomatischen Import von Kontoauszügen.

## Installation

- Öffnen Sie die Installation von Plugins in Jameica über das Menü `Datei->Plugins online suchen...`
- Wählen Sie im Tab `Verfügbare Plugins` das Repository `https://hibiscus.tvbrowser.org` aus.
- Das Plugin _Kontoauszugimport_ wird angezeigt und kann über den Klick auf den Knopf `Installieren...` installiert werden.

Nach einem Neustart von Jameica steht das Plugin zur Verfügung.

## Anwendung

- Öffnen Sie die Liste der vorhandenen Konten in Hibiscus.
- Öffnen Sie das Kontextmenü des Kontos für das der Import eingerichtet werden soll und wählen Sie dort `Importieren von Kontoauszügen konfigurieren...`
- Handelt es sich um eine Bank, für die das Format der Kontoauszüge bekannt ist, wird das Profil für das gewählte Konto mit den richtigen Werten für _Datei-Pattern_ und _Match-Reihenfolge_ gefüllt.
- Sollte die Bank unbekannt sein, wird ein Beispiel für _Datei-Pattern_ eingetragen.
- Um den Import zu ermöglichen, muss der Name der Kontoauszugdatei wenigstens das Jahr und den Monat enthalten. Diese sind dann jeweils als Matching-Gruppe einzutragen und deren Position im Dateinamen unter _Match-Reihenfolge_ anzugeben.
- Die Kontoauszüge können auch vollautomatisch an einen gewünschten Ort verschoben werden, dazu muss der _Ziel-Ordner_ angegeben werden. Soll der Datei dabei ein Prefix vorangestellt werden, kann dieser unter _Umbenennen-Prefix_ angegeben werden.
- Es wird erwartet die Kontoauszugsdateien im _Download-Ordner_ vorzufinden, wo sie anhand des _Datei-Pattern_ dem gewünschten Konto zugeordnert werden.
- Der Import wird im Kontextmenü eines beliebigen Kontos, für das ein Import-Profil angelegt wurde, über `Kontoauszüge importieren...` gestartet. Dabei wird immer der Import für alle Konten gestartet, für die ein Import-Profil angelegt wurde. D.h. wenn im _Download-Ordner_ Kontoauszüge für mehrere Konten liegen, werden diese alle importiert, wenn sie zugeordnet werden konnten.