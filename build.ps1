[CmdletBinding()]
param(
    [ValidateSet('ocdarts', 'ocboltenchant', 'ocfarming', 'ocwalkerprobe')]
    [string]$Plugin = 'ocdarts',
    [string]$HubPath,
    [string]$ClientJar,
    [string]$ClientVersion,
    [string]$PluginsDirectory,
    [string]$JdkVendor,
    [string]$JdkPath,
    [switch]$Offline,
    [switch]$BuildOnly,
    [switch]$Test
)

$ErrorActionPreference = 'Stop'
if (-not $HubPath) {
    $HubPath = Join-Path $PSScriptRoot '..\Microbot-Hub'
}
$hubDirectory = (Resolve-Path -LiteralPath $HubPath).Path
$gradleWrapper = Join-Path $hubDirectory 'gradlew.bat'
if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
    throw "Gradle do Microbot-Hub nao encontrado: $gradleWrapper"
}
if ($ClientJar) {
    $ClientJar = (Resolve-Path -LiteralPath $ClientJar).Path
}
if (-not $PluginsDirectory) {
    if ($BuildOnly) {
        $PluginsDirectory = Join-Path $PSScriptRoot 'dist'
    } else {
        $profileDirectory = [Environment]::GetFolderPath('UserProfile')
        if (-not $profileDirectory) { $profileDirectory = $env:USERPROFILE }
        if (-not $profileDirectory) { throw 'Informe -PluginsDirectory para instalar o plugin.' }
        $PluginsDirectory = Join-Path $profileDirectory '.runelite\microbot-plugins'
    }
}
$installDirectory = [IO.Path]::GetFullPath($PluginsDirectory)

$pluginClass = @{ ocdarts = 'OcDartsPlugin'; ocboltenchant = 'OcBoltEnchantPlugin'; ocfarming = 'OcFarmingPlugin'; ocwalkerprobe = 'OcWalkerProbePlugin' }[$Plugin]
$codeDirectory = Join-Path $hubDirectory "src\main\java\net\runelite\client\plugins\microbot\$Plugin"
$docsDirectory = Join-Path $hubDirectory "src\main\resources\net\runelite\client\plugins\microbot\$Plugin\docs"
$testsDirectory = Join-Path $hubDirectory "src\test\java\net\runelite\client\plugins\microbot\$Plugin"
New-Item -ItemType Directory -Force -Path $codeDirectory, $docsDirectory, $testsDirectory | Out-Null
Copy-Item -Path (Join-Path $PSScriptRoot "$Plugin\*.java") -Destination $codeDirectory
Copy-Item -LiteralPath (Join-Path $PSScriptRoot "$Plugin\README.md") -Destination $docsDirectory
$noticeFile = Join-Path $PSScriptRoot "$Plugin\THIRD_PARTY_NOTICES.txt"
if (Test-Path -LiteralPath $noticeFile -PathType Leaf) {
    Copy-Item -LiteralPath $noticeFile -Destination (Split-Path -Parent $docsDirectory)
}
Copy-Item -Path (Join-Path $PSScriptRoot "tests\$Plugin\*.java") -Destination $testsDirectory

$gradleArguments = @(
    'installCutguardianPlugin',
    '--console=plain',
    '--init-script', (Join-Path $PSScriptRoot 'gradle\install.init.gradle'),
    "-PpluginList=$pluginClass",
    "-PcutguardianPluginClass=$pluginClass",
    "-PcutguardianPluginPackage=$Plugin",
    "-PcutguardianInstallDir=$installDirectory",
    "-PcutguardianDistDir=$(Join-Path $PSScriptRoot 'dist')"
)
if ($ClientJar) { $gradleArguments += "-PmicrobotClientPath=$ClientJar" }
if ($ClientVersion) { $gradleArguments += "-PmicrobotClientVersion=$ClientVersion" }
if ($JdkVendor) { $gradleArguments += "-PcutguardianJdkVendor=$JdkVendor" }
if ($JdkPath) { $gradleArguments += "-Porg.gradle.java.installations.paths=$JdkPath" }
if ($Offline) { $gradleArguments += '--offline' }
if ($BuildOnly) { $gradleArguments += '-PcutguardianBuildOnly=true' }
if ($Test) { $gradleArguments += '-PcutguardianTest=true' }

Push-Location $hubDirectory
try {
    & $gradleWrapper @gradleArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Build/instalacao falhou (Gradle: $LASTEXITCODE)."
    }
} finally {
    Pop-Location
}
