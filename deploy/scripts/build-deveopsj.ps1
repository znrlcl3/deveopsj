[CmdletBinding()]
param(
    [string]$SourcePath = (Join-Path $PSScriptRoot '../..')
)
$ErrorActionPreference = 'Stop'
$source = (Resolve-Path -LiteralPath $SourcePath).Path
$wrapper = Join-Path $source 'mvnw.cmd'
if (-not (Test-Path -LiteralPath $wrapper -PathType Leaf)) { throw 'Maven wrapper not found.' }
Push-Location $source
try {
    & $wrapper clean test '-Dspring.profiles.active=test' | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'Tests failed. No deployment artifact was prepared.' }
    & $wrapper -Pcloud clean package -DskipTests | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'Cloud packaging failed.' }

    [xml]$pom = Get-Content -LiteralPath (Join-Path $source 'pom.xml') -Raw
    $artifact = Join-Path $source "target/$($pom.project.artifactId)-$($pom.project.version).jar"
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($artifact)
    try {
        if ($archive.GetEntry('BOOT-INF/classes/application.properties')) {
            throw 'Deployment JAR contains local application.properties.'
        }
        if (-not $archive.GetEntry('BOOT-INF/classes/application-prod.yml')) {
            throw 'Production profile is missing from the JAR.'
        }
    } finally {
        $archive.Dispose()
    }
    Write-Host "Cloud JAR prepared: $artifact"
    return $artifact
} finally {
    Pop-Location
}
