# cutguardian-microbot-plugins

Microbot plugins by **cutguardian**, organized in one directory per plugin,
following the source layout used by `ksppluginsrelease`.

| Plugin | Version | Minimum Microbot version | Description |
|---|---|---|---|
| [[OC] Darts](ocdarts/README.md) | 1.0.1 | 2.6.22 | Combines feathers and selected dart tips with each click in the game view. |
| [[OC] Bolt Enchant](ocboltenchant/README.md) | 1.0.2 | 2.6.22 | Holds Space while opening Crossbow Bolt Enchantments at a configurable tick interval. |

[Farming Runner 0.9.4](ocfarming/README.md) prepares supplies for pending work from inventory/bank and tends regular trees at six
locations and fruit trees at five: saplings, protection, pruning, health checks,
fruit picking/noting and paid removal. Allotments are excluded.
Built against the local Microbot 2.6.22 walker API. Regular trees have been used
in game; the complete fruit-tree cycle still needs live validation with supplies.

## Structure

```text
ocdarts/            OC Darts Java source and instructions
ocboltenchant/      OC Bolt Enchant Java source and instructions
ocfarming/          Farming Runner monitor and technical documentation
tests/ocdarts/      OC Darts tests
tests/ocboltenchant/ OC Bolt Enchant tests
tests/ocfarming/    Farming Runner tracker tests
build.cmd          Windows build command
build.ps1          Builds and optionally installs the selected plugin into Microbot
gradle/            Installation task used by build.ps1
dist/              Local JAR files, ignored by Git
```

Java packages are `net.runelite.client.plugins.microbot.<plugin-directory>`.
The repository name does not change the package or the plugin's display name.

## Build and install

Run from the repository root:

```powershell
.\build.cmd
```

OC Darts remains the default. Use `-Plugin ocboltenchant` for OC Bolt Enchant.
Use `-Plugin ocfarming` for the Farming Runner monitor.
Add `-BuildOnly` to save the JAR in `dist/` without installing, and `-Test` to run
the selected plugin's tests before copying the artifact:

```powershell
.\build.cmd -Plugin ocboltenchant -BuildOnly -Test -Offline `
    -ClientJar '..\Microbot\runelite-client\build\libs\microbot-2.6.22.jar' `
    -ClientVersion '2.6.22'
```

The script updates the selected plugin's sources in `..\Microbot-Hub`, builds them with
that project's Gradle wrapper, and automatically copies the JAR to
`%USERPROFILE%\.runelite\microbot-plugins\OcDartsPlugin.jar`.
On the current development machine, the destination is
`C:\Users\Renan\.runelite\microbot-plugins\OcDartsPlugin.jar`.
OC Bolt Enchant uses `OcBoltEnchantPlugin.jar` in the same installation directory.

`build.cmd` runs `build.ps1` with script execution enabled only for that process,
without changing the permanent PowerShell execution policy.

The installed filename stays the same so subsequent builds replace the same copy.
The versioned JAR is also saved in `dist/` and `Microbot-Hub/build/libs/`.
A compilation failure prevents installation. The copy still runs when the JAR
is already up to date. Installation does not reload a running plugin; restart
the client to use the new JAR.

To use a different Hub directory, installation directory, or local client JAR:

```powershell
.\build.cmd -HubPath '..\Microbot-Hub' `
    -ClientJar '..\Microbot\runelite-client\build\libs\microbot-2.6.22.jar' `
    -ClientVersion '2.6.22' -Offline `
    -PluginsDirectory 'C:\Users\Renan\.runelite\microbot-plugins'
```

`-Offline` requires the dependencies to be available in the Gradle cache.
Running the Hub's JAR task directly only builds the artifact; use `build.cmd`
to build and install it.

## Validation

OC Darts 1.0.0 was built against the local Microbot 2.6.22 client, and all seven
unit tests passed before the sources were copied into this repository.
Version 1.0.1 updates the author metadata and documentation.
In-game validation is still pending. See the checklist in the
[plugin README](ocdarts/README.md).

OC Bolt Enchant 1.0.2 was compiled against the local Microbot 2.6.22 client;
its thirteen tests passed. It keeps Space pressed across menu rebuilds and
reopens the spell on game ticks. Live validation of the new one-tick cadence
is pending unenchanted bolts; see its [checklist](ocboltenchant/README.md#in-game-validation-checklist).

## GitHub

Track source files, tests, and documentation. Publish JARs as GitHub release
attachments; `dist/` and `.jar` files are excluded from Git history.

For each update, increment the version referenced by `@PluginDescriptor`,
update the documentation, then build and validate before publishing a release.
