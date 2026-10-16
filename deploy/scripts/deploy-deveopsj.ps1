[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$IdentityFile,
    [string]$KnownHostsFile,
    [string]$SourcePath = (Join-Path $PSScriptRoot '../..'),
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9.-]*$')][string]$Server = '129.225.200.188',
    [ValidatePattern('^[a-z_][a-z0-9_-]*$')][string]$SshUser = 'ubuntu',
    [ValidateRange(1,65535)][int]$SshPort = 22
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ssh-options.ps1')
Get-Command ssh, scp -ErrorAction Stop | Out-Null
$options = @(Get-DeveopsjSshOptions -IdentityFile $IdentityFile -KnownHostsFile $KnownHostsFile)
$artifact = & (Join-Path $PSScriptRoot 'build-deveopsj.ps1') -SourcePath $SourcePath
$checksum = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLowerInvariant()
$id = [Guid]::NewGuid().ToString('N')
$remoteJar = "/tmp/deveopsj-$id.jar"
$remoteScript = "/tmp/deveopsj-$id.sh"
$target = "$SshUser@$Server"
& scp @options -P $SshPort $artifact "${target}:$remoteJar"
if ($LASTEXITCODE -ne 0) { throw 'JAR upload failed.' }
& scp @options -P $SshPort (Join-Path $PSScriptRoot 'replace-app.sh') "${target}:$remoteScript"
if ($LASTEXITCODE -ne 0) { throw "Script upload failed; uploaded JAR remains at $remoteJar" }
& ssh @options -p $SshPort $target "sudo -n bash $remoteScript $remoteJar $checksum"
if ($LASTEXITCODE -ne 0) { throw 'Server deployment failed. Review the remote result; do not assume the new JAR is running.' }
Write-Host 'Deployment completed; application and database health check passed.'
