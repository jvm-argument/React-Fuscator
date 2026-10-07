$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectDirectory
try {
    & mvn -B -ntp package
    if ($LASTEXITCODE -ne 0) { throw 'Build or tests failed' }
    New-Item -ItemType Directory -Path dist -Force | Out-Null
    Copy-Item -LiteralPath target/react-fuscator.jar -Destination dist/React-Fuscator.jar
    Write-Output 'Built dist/React-Fuscator.jar'
} finally { Pop-Location }
