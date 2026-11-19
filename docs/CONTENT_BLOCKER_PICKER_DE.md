# Hilfe: Video Ads & Content Blocker und Element-Picker

Die Funktionen benötigen ein vom eBlocker geschütztes Gerät. Für HTTPS-Seiten muss die HTTPS-Unterstützung für dieses Gerät funktionieren; eine HTTPS-App-Ausnahme kann die Inhaltsbearbeitung für die betroffene Seite verhindern.

## Element auf einer Webseite ausblenden

1. Öffne die betreffende Webseite und die orange eBlocker-ControlBar. Wähle **Element**.
2. Klicke das Element an. Für verdeckte Elemente helfen die Auswahl **Darunter** und die Wahl eines übergeordneten Elements. Prüfe die orange Markierung und die vorgeschlagene Domain-Regel.
3. **Vorschau** zeigt die Ausblendung vorübergehend. **Erneut auswählen** verwirft die Auswahl.
4. Klicke im eBlocker-Dialog **Prüfen**. Die Regel muss zur aktuellen Domain passen und genau einen Treffer auf dieser Seite haben. Eine besonders große Ausblendung braucht eine zusätzliche Bestätigung.
5. Klicke **Übernehmen**. Die Regel wird einmal in **Eigene Element-Hiding-Regeln** auf dem eBlocker gespeichert. Bei einem Duplikat wird kein zweiter Eintrag erzeugt.
6. Nach erfolgreicher Übernahme schließt sich der Speicherdialog, das Regelfeld wird geleert und der Picker wartet direkt auf das nächste Element. **Esc** oder **Schließen** beendet ihn.

Die Picker-Oberfläche folgt der Browsersprache: Deutsch bei `de`/`de-*`, sonst Englisch. Das Sprache-Menü der eBlocker-Einstellungen allein steuert sie nicht.

## Eigene Regeln verwalten

Unter **Einstellungen → Blocker → Video Ads & Content Blocker → Eigene Element-Hiding-Regeln** kannst du Regeln ansehen, bearbeiten und den gesamten eigenen Regelbereich ein- oder ausschalten. Eine Regel hat zum Beispiel die Form `www.example.org##.hinweis`. Prüfe die Domain und den CSS-Selektor, bevor du speicherst. Sehr lange, automatisch erzeugte IDs und `nth-of-type(...)` können nach einem Seitenupdate wirkungslos oder zu breit werden.

Der Picker prüft den momentanen Seitenzustand. Er kann nicht garantieren, dass die Regel nach einem Webseiten-Update weiterhin passend ist. Falls Inhalte verschwinden, deaktiviere die zuletzt geänderte eigene Regel und lade die Seite neu.

## Weitere Content-Liste hinzufügen

Unter **Einstellungen → Blocker → Video Ads & Content Blocker → Hinzufügen** wählst du einen Namen und entweder **Eigene Liste pflegen** (eine Regel pro Zeile) oder **Download URL angeben**. Das Format **easylist** ist hier fest voreingestellt, weil aktuell kein anderes Format zur Auswahl steht. Danach speicherst du die Liste und prüfst ihren Schalter.

Dieser Bereich verarbeitet unterstützte kosmetische `##`-Regeln, Ausnahmen `#@#` und vorhandene Scriptlet-Regeln `+js(...)`. Netzwerkregeln wie `||example.org^` werden in diesem Bereich nicht angewendet. Eine Liste kann deshalb den Status **READY** haben, obwohl sie hier keine wirksame Content-Regel enthält. Teste neue Listen zuerst klein und prüfe betroffene Seiten.

Die integrierte Liste **uBlock Filters** und deine eigenen Listen haben getrennte Schalter. **Eigene Element-Hiding-Regeln** sind ebenfalls ein eigener Bereich. Das Abschalten einer Liste löscht ihre Regeln nicht.

## Scriptlets 2.0

Der Scriptlets-2.0-Build enthält eine fest gepinnte Auswahl von uBlock-Scriptlets, die der eBlocker ohne Browser-Plug-in in inspizierte HTML-Seiten einfügt. Einige ältere Namen bleiben als Fallback vorhanden. Nicht jede uBlock-Funktion und nicht jede Website wird unterstützt. Die Funktion hängt unter anderem von HTTPS-Unterstützung und der Content-Security-Policy der Seite ab. Ein Scriptlet ist nur dann sinnvoll zu testen, wenn eine aktivierte Liste eine passende Domain-Regel enthält.

## Wenn eine Seite nicht mehr funktioniert

Notiere URL und Uhrzeit. Prüfe zuerst Browser-Erweiterungen und lokal gespeicherte Regeln. Deaktiviere danach auf dem eBlocker nacheinander die zuletzt geänderten eigenen Regeln, die betreffende Content-Liste und weitere Filter; lade nach **jeder einzelnen Änderung** neu. Stelle die ursprünglichen Werte anschließend wieder her. Eine Weiterleitung auf `bild.de/adblockwall.html` ist ein Hinweis auf Blockererkennung, aber kein Beweis für eine bestimmte eBlocker-Regel.

Bei `ERR_ICAP_FAILURE` oder Verbindungsabbrüchen notiere Zeitpunkt und Gerät und prüfe eBlocker-/Netzstatus. Ein neu gestarteter ICAP-Dienst kann kurzzeitig nicht antworten. Wiederholte Fehler brauchen eine gesonderte Netz- und Dienstdiagnose.
