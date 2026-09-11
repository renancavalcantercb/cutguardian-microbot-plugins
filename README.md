# cutguardian-microbot-plugins

Plugins para Microbot, organizados em uma pasta por plugin, seguindo o formato
de fontes do `ksppluginsrelease`.

| Plugin | Versao | Microbot minimo | Descricao |
|---|---|---|---|
| [[OC] Darts](ocdarts/README.md) | 1.0.0 | 2.6.22 | Combina pena e ponta de dardo a cada clique na area do jogo. |

## Estrutura

```text
ocdarts/             Codigo Java e instrucoes do OC Darts
tests/ocdarts/       Testes do OC Darts
dist/               JARs locais, ignorados pelo Git
```

O pacote Java do OC Darts e `net.runelite.client.plugins.microbot.ocdarts`.
O nome deste repositorio nao altera o pacote nem o nome exibido no cliente.

## Compilar

Este repositorio guarda os fontes dos plugins. A compilacao usa o projeto
Microbot-Hub e suas dependencias; nao existe um Gradle independente aqui.

Com este repositorio e o Microbot-Hub lado a lado, execute no PowerShell a partir
da raiz deste repositorio:

```powershell
$hub = '..\Microbot-Hub'
$code = "$hub\src\main\java\net\runelite\client\plugins\microbot\ocdarts"
$docs = "$hub\src\main\resources\net\runelite\client\plugins\microbot\ocdarts\docs"
$tests = "$hub\src\test\java\net\runelite\client\plugins\microbot\ocdarts"
New-Item -ItemType Directory -Force -Path $code, $docs, $tests | Out-Null
Copy-Item 'ocdarts\*.java' -Destination $code
Copy-Item 'ocdarts\README.md' -Destination $docs
Copy-Item 'tests\ocdarts\*.java' -Destination $tests
Push-Location $hub
try {
    .\gradlew.bat OcDartsPluginJar '-PpluginList=OcDartsPlugin'
} finally {
    Pop-Location
}
```

O comando atualiza a copia de trabalho do plugin no Microbot-Hub. O JAR e gerado
em `Microbot-Hub/build/libs/OcDartsPlugin-1.0.0.jar`.
O Hub aceita `-PmicrobotClientPath=<caminho-do-jar>` para compilar com um cliente local.

## Validacao

O OC Darts 1.0.0 foi compilado com o cliente local Microbot 2.6.22, e seus sete
testes unitarios passaram antes da copia dos fontes para este repositorio.
A validacao dentro do jogo ainda esta pendente. Consulte o roteiro no
[README do plugin](ocdarts/README.md).

## GitHub

Versione os fontes, testes e documentacao. Publique os JARs como anexos de
releases do GitHub; `dist/` e os arquivos `.jar` ficam fora do historico Git.

Para cada atualizacao, incremente a versao no `@PluginDescriptor`, atualize a
documentacao, compile e valide antes de publicar a release.
