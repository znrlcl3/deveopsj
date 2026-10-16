[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9_]+$')][string]$DatabaseName,
    [Parameter(Mandatory)][string]$DatabaseUser,
    [string]$SourcePath = (Join-Path $PSScriptRoot '../..'),
    [ValidateRange(1,65535)][int]$DatabasePort = 13306,
    [ValidateRange(1,65535)][int]$AppPort = 8080
)
$ErrorActionPreference = 'Stop'
$source = (Resolve-Path -LiteralPath $SourcePath).Path
$wrapper = Join-Path $source 'mvnw.cmd'
if (-not (Test-Path -LiteralPath $wrapper -PathType Leaf)) { throw 'Maven wrapper not found.' }
$connection = [Net.Sockets.TcpClient]::new()
try {
    $pending = $connection.ConnectAsync('127.0.0.1', $DatabasePort)
    if (-not $pending.Wait(3000) -or -not $connection.Connected) {
        throw 'Database SSH tunnel is not running.'
    }
} catch {
    throw 'Open the database SSH tunnel before starting this application.'
} finally {
    $connection.Dispose()
}
$previous = @{}
foreach ($name in @('CLOUD_DB_NAME','CLOUD_DB_USERNAME','CLOUD_DB_PASSWORD','CLOUD_DB_LOCAL_PORT','CLOUD_DEV_PORT')) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
$password = Read-Host 'Shared database password (not saved)' -AsSecureString
$passwordPtr = [IntPtr]::Zero
try {
    $passwordPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($password)
    $env:CLOUD_DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPtr)
    $env:CLOUD_DB_NAME = $DatabaseName
    $env:CLOUD_DB_USERNAME = $DatabaseUser
    $env:CLOUD_DB_LOCAL_PORT = [string]$DatabasePort
    $env:CLOUD_DEV_PORT = [string]$AppPort
    Write-Host "Local app: http://localhost:$AppPort. Writes affect the SHARED database."
    Push-Location $source
    try {
        & $wrapper spring-boot:run '-Dspring-boot.run.profiles=cloud-db' -DskipTests
        if ($LASTEXITCODE -ne 0) { throw 'Shared-data development application failed.' }
    } finally {
        Pop-Location
    }
} finally {
    if ($passwordPtr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPtr) }
    $password.Dispose()
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
}
