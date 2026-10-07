param(
    [string]$MinecraftDirectory = "$env:APPDATA/.minecraft",
    [string]$PaperLibraries = 'work/paper-1.21.11/libraries',
    [string]$FabricGameDirectory = 'work/fabric-1.21.4'
)
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectDirectory
try {
    $obfuscator = 'dist/React-Fuscator.jar'
    if (-not (Test-Path -LiteralPath $obfuscator)) { throw 'Run scripts/Build.ps1 first.' }
    $aperInput = 'плагины для теста/AperEvent-1.6.2.jar'
    $legendaryInput = 'плагины для теста/LegendaryWeapon-2.0.6.jar'
    $xeronInput = 'мод для теста/xeron-1.0.0.jar'
    $intermediary = Join-Path $FabricGameDirectory '.fabric/remappedJars/minecraft-1.21.4-0.19.3/client-intermediary.jar'
    $processedMods = Join-Path $FabricGameDirectory '.fabric/processedMods'
    foreach ($path in @($obfuscator,$aperInput,$legendaryInput,$xeronInput,$PaperLibraries,$intermediary,$processedMods)) {
        if (-not (Test-Path -LiteralPath $path)) { throw "Missing test input or dependency: $path" }
    }
    & java -jar $obfuscator obfuscate $aperInput -o work/outputs/AperEvent-extreme.jar -p extreme -l $PaperLibraries -l $legendaryInput --seed 20261008
    if ($LASTEXITCODE -ne 0) { throw 'AperEvent processing failed.' }
    & java -jar $obfuscator obfuscate $legendaryInput -o work/outputs/LegendaryWeapon-extreme.jar -p extreme -l $PaperLibraries -l $aperInput --seed 20261008
    if ($LASTEXITCODE -ne 0) { throw 'LegendaryWeapon processing failed.' }
    & java -jar $obfuscator obfuscate $xeronInput -o work/outputs/xeron-full-extreme.jar -p extreme --rename-serialization -l $intermediary -l (Join-Path $MinecraftDirectory 'libraries') -l $processedMods --seed 20261008
    if ($LASTEXITCODE -ne 0) { throw 'Xeron processing failed.' }
} finally { Pop-Location }
