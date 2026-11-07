JAR BAUEN (ohne etwas zu installieren):
1. Kostenlosen Account auf github.com, neues Repository erstellen.
2. Alle Dateien dieses Ordners hochladen (inkl. Ordner .github).
3. Tab "Actions" -> "build" -> nach ~3 Min unter "Artifacts" die cleancontrols-jar herunterladen.
4. Die .jar (NICHT die -sources.jar) in .minecraft/mods legen. Benoetigt Fabric Loader fuer 1.21.11.

Falls der Build wegen Versionen fehlschlaegt: auf https://fabricmc.net/develop
Minecraft 1.21.11 waehlen und yarn_mappings / loader_version in gradle.properties anpassen.
