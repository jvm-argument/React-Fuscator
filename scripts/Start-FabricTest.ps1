param(
    [string]$MinecraftDirectory = "$env:APPDATA/.minecraft",
    [string]$Version = 'fabric-loader-0.19.3-1.21.4',
    [string]$GameDirectory = 'work/fabric-1.21.4',
    [string]$Java = 'C:/Program Files/Java/graalvm-jdk-21.0.7+8.1/bin/java.exe'
)
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$gamePath = [IO.Path]::GetFullPath((Join-Path $projectDirectory $GameDirectory))
if (-not $gamePath.StartsWith($projectDirectory + [IO.Path]::DirectorySeparatorChar)) { throw 'Test game directory must be inside this project.' }
$versionPath = Join-Path $MinecraftDirectory "versions/$Version"
$metadata = Get-Content -LiteralPath (Join-Path $versionPath "$Version.json") -Raw | ConvertFrom-Json
$classPath = [Collections.Generic.List[string]]::new()
foreach ($library in $metadata.libraries) {
    $allowed = $true
    if ($library.rules) {
        $allowed = $false
        foreach ($rule in $library.rules) {
            $matches = (-not $rule.os) -or (($rule.os.name -eq 'windows') -and ((-not $rule.os.arch) -or ($rule.os.arch -eq 'x86_64')))
            if ($matches) { $allowed = $rule.action -eq 'allow' }
        }
    }
    if (-not $allowed) { continue }
    $coordinates = $library.name.Split(':')
    if ($coordinates.Count -gt 3 -and $coordinates[3] -like 'natives-*' -and $coordinates[3] -ne 'natives-windows') { continue }
    $relative = $coordinates[0].Replace('.','/') + '/' + $coordinates[1] + '/' + $coordinates[2] + '/' + $coordinates[1] + '-' + $coordinates[2]
    if ($coordinates.Count -gt 3) { $relative += '-' + $coordinates[3] }
    $relative += '.jar'
    $path = Join-Path $MinecraftDirectory "libraries/$relative"
    if (-not (Test-Path -LiteralPath $path)) { throw "Missing client library: $relative" }
    $classPath.Add($path)
}
$classPath.Add((Join-Path $versionPath "$Version.jar"))
New-Item -ItemType Directory -Path $gamePath -Force | Out-Null
Set-Content -LiteralPath (Join-Path $gamePath 'options.txt') -Value @('renderDistance:2','simulationDistance:5','maxFps:30','fullscreen:false','soundCategory_master:0.0') -Encoding utf8
$arguments = @('-Xmx2G','-Xverify:all','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-Dfabric.log.disableAnsi=true','-cp',($classPath -join ';'),$metadata.mainClass,'--username','ReactTest','--version',$Version,'--gameDir',$gamePath,'--assetsDir',(Join-Path $MinecraftDirectory 'assets'),'--assetIndex',[string]$metadata.assetIndex.id,'--accessToken','0','--uuid','00000000000000000000000000000001','--userType','legacy','--width','854','--height','480')
# Only an isolated offline test identity is used; launcher account files are never read.
Push-Location -LiteralPath $gamePath
try { & $Java @arguments; $clientExitCode = $LASTEXITCODE } finally { Pop-Location }
Write-Output "Fabric client process exit code: $clientExitCode"
exit $clientExitCode
