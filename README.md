# HorrorOlympics Alpha 1 – Paper 1.21.11

**Status: Quellcode / Testversion. Nicht kompiliert und noch nicht auf Paper getestet.**

## Kompilieren

Voraussetzungen: Java JDK 21, Maven, Internetzugang für die Paper-API.

Im Projektordner `mvn clean package` ausführen. Die JAR liegt anschließend unter `target/HorrorOlympics-Alpha1.jar`. Diese in `plugins/` kopieren und den Server neu starten.

## Befehle

- `/ho help` – Übersicht
- `/ho setlobby` – Lobby speichern (Admin)
- `/ho arena apored pos1` und `pos2` – Grenzen speichern (Admin)
- `/ho arena apored spawn` – Teilnehmer-Spawn (Admin)
- `/ho arena apored hunter` – Jäger-Spawn (Admin)
- `/ho arena apored spectator` – Zuschauerposition (Admin)
- `/ho arena apored show` – Grenz-Ecken anzeigen (Admin)
- `/ho join`, `/ho leave`, `/ho lobby` – Spielerbefehle
- `/ho hunter <Spieler>` – Jäger auswählen (Admin)
- `/ho start apored`, `/ho stop`, `/ho status` – Steuerung (Admin)
- `/ho points [Spieler]` – Punkte anzeigen
- `/ho points set|add|remove <Online-Spieler> <Zahl>` – Punkte ändern (Admin)

## Was tatsächlich implementiert ist

- Speicherung der Arenapositionen und Punkte in `config.yml`
- 25 Teilnehmer, Jäger muss außerhalb der Teilnehmerliste sein
- ApoRed-Runde: 30 Sekunden Versteckzeit, 8 Minuten Jagdzeit, Sneaker-Treffer, Spectator, manueller Stopp
- Größen-Attribut 1.5 und Speed II für Jäger
- Spielerinventar, Spielmodus, Position, Potion-Effekte und Größe werden nach der Runde wiederhergestellt

## Bekannte Einschränkungen / offene Tests

- **Kein Skinwechsel**: `apored.png` ist nicht integriert; dafür ist eine separate Lösung nötig.
- **Keine Musik oder Distanz-Sounds**: dafür ist ein Resourcepack und ein Sound-System nötig.
- **Keine automatische Punktevergabe**: die Admin-Punktebefehle funktionieren unabhängig von den Runden.
- **Keine automatische Erkennung von unerlaubtem Verlassen der Arena**.
- **Keine Crash-Recovery**: bei Serverabsturz während einer Runde wird der gespeicherte Spielerzustand nicht dauerhaft gesichert.
- Der Jäger kann sich während der ersten 30 Sekunden aufgrund anderer Mechaniken bewegen; die Spawnposition sollte deshalb ein geschlossener Warteraum sein.
- Die vier anderen Minigames sind noch nicht implementiert.
- Die Version ist nicht gegen die echte Paper-API kompiliert oder auf einem laufenden Server getestet. Bitte erst auf einem **Testserver mit Welt-Backup** ausprobieren.
