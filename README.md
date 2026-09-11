# cutguardian-microbot-plugins

Microbot plugins by **cutguardian**, organized in one directory per plugin,
following the source layout used by `ksppluginsrelease`.

| Plugin | Version | Minimum Microbot version | Description |
|---|---|---|---|
| [[OC] Darts](ocdarts/README.md) | 1.0.1 | 2.6.22 | Combines feathers and selected dart tips with each click in the game view. |

## Structure

```text
ocdarts/            OC Darts Java source and instructions
tests/ocdarts/      OC Darts tests
build.cmd          Windows build command
build.ps1          Builds and installs OC Darts into Microbot
gradle/            Installation task used by build.ps1
dist/              Local JAR files, ignored by Git
```

The OC Darts Java package is `net.runelite.client.plugins.microbot.ocdarts`.
The repository name does not change the package or the plugin's display name.

## Build and install

Run from the repository root:

```powershell
.\build.cmd
```

The script updates the OC Darts sources in `..\Microbot-Hub`, builds them with
that project's Gradle wrapper, and automatically copies the JAR to
`%USERPROFILE%\.runelite\microbot-plugins\OcDartsPlugin.jar`.
On the current development machine, the destination is
`C:\Users\Renan\.runelite\microbot-plugins\OcDartsPlugin.jar`.

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

## GitHub

Track source files, tests, and documentation. Publish JARs as GitHub release
attachments; `dist/` and `.jar` files are excluded from Git history.

For each update, increment the version referenced by `@PluginDescriptor`,
update the documentation, then build and validate before publishing a release.
