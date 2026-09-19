# Jobradar 0.1 – GitHub-Build

Private Android-App für die persönliche Jobsuche.

## APK automatisch mit GitHub bauen

1. Neues leeres GitHub-Repository `jobradar` anlegen.
2. Den Inhalt dieses Projekts in das Repository hochladen.
3. GitHub öffnet nach dem Upload automatisch den Workflow **Build Jobradar APK**.
4. Unter **Actions → Build Jobradar APK** den erfolgreichen Lauf öffnen.
5. Unter **Artifacts** `Jobradar-APK` herunterladen.
6. ZIP entpacken und `app-debug.apk` auf Android installieren.

Der Workflow läuft außerdem bei jeder Änderung auf `main`.

## Stand dieser Version
- native Android-App in Kotlin/Jetpack Compose
- Oberfläche: Neu / Interessant / Beworben / Ausgeblendet
- persönliches regelbasiertes Matching als Code vorhanden
- lokale Room-Datenbank vorbereitet
- Netzwerk- und WorkManager-Abhängigkeiten vorbereitet
- aktuell zwei Demo-Karten, damit zuerst der komplette APK-Build/Installationsweg geprüft werden kann

## Danach
Nach erfolgreichem Installations-Test werden die Demo-Karten durch echte Stellenquellen ersetzt und
Statusspeicherung, täglicher Hintergrundabruf und Benachrichtigungen angeschlossen.
