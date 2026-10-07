param([switch]$Check)

$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$cachePath = Join-Path $projectPath '.style-cache'
New-Item -ItemType Directory -Force -Path $cachePath | Out-Null
$dependencies = @{
    'javaparser.jar' = 'https://repo.maven.apache.org/maven2/com/github/javaparser/javaparser-core/3.26.3/javaparser-core-3.26.3.jar'
    'google-java-format-1.37.0.jar' = 'https://repo.maven.apache.org/maven2/com/google/googlejavaformat/google-java-format/1.37.0/google-java-format-1.37.0-all-deps.jar'
}

foreach ($dependency in $dependencies.GetEnumerator()) {
    $dependencyPath = Join-Path $cachePath $dependency.Key
    if (-not (Test-Path -LiteralPath $dependencyPath)) {
        Invoke-WebRequest -Uri $dependency.Value -OutFile $dependencyPath
    }
}

$classPath = @((Join-Path $cachePath 'javaparser.jar'), (Join-Path $cachePath 'google-java-format-1.37.0.jar')) -join [IO.Path]::PathSeparator
& javac -encoding UTF-8 -cp $classPath -d $cachePath (Join-Path $projectPath 'tools/style/JavaStyleTool.java')
if ($LASTEXITCODE -ne 0) {
    throw 'Style tool compilation failed'
}

$exports = @('api', 'file', 'parser', 'tree', 'util', 'code') | ForEach-Object { "--add-exports=jdk.compiler/com.sun.tools.javac.$_=ALL-UNNAMED" }
$styleArguments = @($projectPath)
if ($Check) {
    $styleArguments += '--check'
}

& java @exports -cp ($cachePath + [IO.Path]::PathSeparator + $classPath) JavaStyleTool @styleArguments
if ($LASTEXITCODE -ne 0) {
    throw 'Java style validation failed'
}
