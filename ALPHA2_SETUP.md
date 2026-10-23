# HorrorOlympics Alpha 2 — TESTVERSION

Wichtig: Das ist ein spielbarer erster Prototyp, keine fertige Release-Version. Vor dem Update den kompletten Serverordner `plugins/HorrorOlympics/` sichern. Die bestehende config.yml wird nicht überschrieben. Die bisherigen Arena-Namen bleiben: apored, hotel, supermarkt, schule, friedhof.

## Installation
Die Dateien aus diesem ZIP ins bestehende GitHub-Repository übernehmen (alte Java-Datei, pom.xml und plugin.yml ersetzen). GitHub Actions erstellt unter Actions -> Build -> Artifacts die neue `HorrorOlympics-Alpha2.jar`. Vorherige JAR vom Server entfernen, neue JAR einfügen und Server vollständig neu starten.

## Befehle
- `/ho join` (jeder Teilnehmer)
- `/ho start <apored|hotel|supermarkt|schule|friedhof>`
- `/ho stop`
- `/ho revive <Spieler>`
- `/ho marker hotel closet add` (an jedem Schrank)
- `/ho marker supermarkt crate add` (an jeder Kiste)
- `/ho marker supermarkt checkout` (an der Kasse)
- `/ho marker schule finish` (an der Ziellinie)
- `/ho marker friedhof skull add`, `bone add`, `candle add`, `beer add`, `eye add` (an Fundorten)
- `/ho marker friedhof delivery` (Abgabe)

Marker: Stelle dich an den Ort und führe den Befehl aus. Für mehrere Positionen `add` benutzen. Rechtsklick in der Nähe eines Markers löst Interaktion aus; es werden keine Blöcke verändert. Die Marker stehen in der bestehenden config.yml.

## Aktuelle Grenzen
- Hotel: 45-Sekunden-Zyklen, nach 25 Sekunden Warnung, nach 35 Sekunden Monstercheck. Schrank-Sicherheit wird per Rechtsklick aktiviert; **noch keine Begrenzung pro Schrank und keine echte Monster-KI**.
- Supermarkt: Kisten per Rechtsklick aufnehmen, an Kasse abgeben. **Noch kein Lichtausfall, keine Hausmeister-KI und kein individuelles Kisten-Respawn**; Warnmeldung nach 120 Sekunden.
- Schule: Lehrerphase wechselt alle 12 Sekunden, Bewegung wird blockweise erkannt. **Noch keine Lehrer-NPC-Drehung**.
- Friedhof: Fünf Fundstücke pro Spieler, Abgabe per Rechtsklick. **Noch kein Totengräber-NPC**.
- ApoRed: bestehende menschliche Jägermechanik; ausgeschiedene Spieler werden jetzt zur Lobby teleportiert, überlebende bekommen Punkte. **Skinwechsel und Sound-Annäherung fehlen noch**.
- Punkte: ApoRed nach Überlebenszeit, Hotel/Schule Überlebende +30, Schule Ziel +40, Supermarkt pro Kiste +5, Friedhof kompletter Satz +50. Das ist eine vorläufige Balance.
- `/ho revive` ist ein Admin-Notfallbefehl. Beim Spielende wird der vorherige Zustand inklusive Position wiederhergestellt.

Bitte mit Testspielern zunächst auf einem separaten Testserver testen. GitHub-Actions-Build prüfen, bevor die JAR auf den Live-Server kommt.
