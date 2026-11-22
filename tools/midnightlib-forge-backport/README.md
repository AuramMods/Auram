# MidnightLib startup fix for Auram

Forge can construct multiple mods in parallel. In MidnightLib 1.9.2 for Forge 1.20.1, their public `MidnightConfig.init` calls can both modify the shared config tables while one call iterates over them. Auram reproduced a `ConcurrentModificationException` while loading Cull Leaves.

The backport serializes `MidnightConfig.init` on the class monitor. The original methods, fields, entry order and config files still use the library's existing implementation. This applies the same synchronization principle as the later MidnightLib fix while preserving the legacy field descriptor. [Upstream fix](https://github.com/TeamMidnightDust/MidnightLib/commit/1fe69729eea8)

Use the original MidnightLib 1.9.2 JAR for Forge 1.20.1 and the ASM JAR from the installed Forge libraries. Build with Java 17 or later.

```sh
javac --release 17 -cp "$asm" -d "$classes" tools/midnightlib-forge-backport/SerializeMidnightConfig.java
java -cp "$classes:$asm" SerializeMidnightConfig "$original" "$backport"
```

Use a new output path for the backport. Use that JAR in place of the original MidnightLib JAR in a disposable copy of Auram. Keep the existing MidnightLib config files and dependent mods. The procedure is identical on Windows with a semicolon in the Java classpath.
