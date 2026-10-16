[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$IdentityFile,
    [string]$KnownHostsFile,
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9.-]*$')][string]$Server = '129.225.200.188',
    [ValidatePattern('^[a-z_][a-z0-9_-]*$')][string]$SshUser = 'ubuntu',
    [ValidateRange(1,65535)][int]$SshPort = 22,
    [ValidateRange(1,65535)][int]$LocalPort = 18080,
    [ValidateRange(1,65535)][int]$RemotePort = 8080,
    [switch]$DatabaseTunnel,
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9.-]*$')][string]$RemoteDatabaseHost = '127.0.0.1',
    [ValidateRange(1,65535)][int]$RemoteDatabasePort = 3306,
    [ValidateRange(1,65535)][int]$LocalDatabasePort = 13306,
    [switch]$Shell
)
$ErrorActionPreference = 'Stop'
if ($Shell -and $DatabaseTunnel) { throw 'Select Shell or DatabaseTunnel, not both.' }
. (Join-Path $PSScriptRoot 'ssh-options.ps1')
Get-Command ssh -ErrorAction Stop | Out-Null
$options = @(Get-DeveopsjSshOptions -IdentityFile $IdentityFile -KnownHostsFile $KnownHostsFile)
if ($Shell) {
    & ssh @options -p $SshPort -t "$SshUser@$Server"
} elseif ($DatabaseTunnel) {
    Write-Host "Database tunnel: 127.0.0.1:$LocalDatabasePort -> server ${RemoteDatabaseHost}:$RemoteDatabasePort. Keep this window open."
    & ssh @options -p $SshPort -N -o ExitOnForwardFailure=yes -L "127.0.0.1:${LocalDatabasePort}:${RemoteDatabaseHost}:${RemoteDatabasePort}" "$SshUser@$Server"
} else {
    Write-Host "Tunnel: 127.0.0.1:$LocalPort -> server 127.0.0.1:$RemotePort. Keep this window open."
    & ssh @options -p $SshPort -N -o ExitOnForwardFailure=yes -L "127.0.0.1:${LocalPort}:127.0.0.1:${RemotePort}" "$SshUser@$Server"
}
if ($LASTEXITCODE -ne 0) { throw "SSH exited with code $LASTEXITCODE" }
