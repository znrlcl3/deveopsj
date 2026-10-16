[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9_]+$')][string]$DatabaseName,
    [Parameter(Mandatory)][string]$DatabaseUser,
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9.-]*$')][string]$DatabaseHost = '127.0.0.1',
    [ValidateRange(1,65535)][int]$DatabasePort = 3306,
    [string]$MysqldumpPath
)
$ErrorActionPreference = 'Stop'
if (-not $MysqldumpPath) {
    $command = Get-Command mysqldump -ErrorAction SilentlyContinue
    if ($command) {
        $MysqldumpPath = $command.Source
    } else {
        foreach ($candidate in @(
            (Join-Path $env:ProgramFiles 'MySQL/MySQL Server 8.0/bin/mysqldump.exe'),
            (Join-Path $env:ProgramFiles 'MySQL/MySQL Workbench 8.0/mysqldump.exe')
        )) {
            if (Test-Path -LiteralPath $candidate -PathType Leaf) { $MysqldumpPath = $candidate; break }
        }
    }
}
if (-not $MysqldumpPath -or -not (Test-Path -LiteralPath $MysqldumpPath -PathType Leaf)) {
    throw 'mysqldump was not found. Supply -MysqldumpPath.'
}
$repository = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$backupDir = Join-Path $repository '.local-data'
[IO.Directory]::CreateDirectory($backupDir) | Out-Null
$identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$acl = Get-Acl -LiteralPath $backupDir
$acl.SetAccessRuleProtection($true, $false)
$rule = [Security.AccessControl.FileSystemAccessRule]::new(
    $identity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
$acl.SetAccessRule($rule)
Set-Acl -LiteralPath $backupDir -AclObject $acl
$id = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N')
$dump = Join-Path $backupDir "$DatabaseName-$id.sql"
$partial = "$dump.partial"
Write-Host 'Read-only database export. Enter the source MySQL password when prompted.'
$argsForDump = @("--host=$DatabaseHost", "--port=$DatabasePort", "--user=$DatabaseUser",
    '--password', '--single-transaction', '--quick', '--no-tablespaces',
    '--set-gtid-purged=OFF', '--default-character-set=utf8mb4',
    '--skip-add-drop-table', '--routines', '--events', '--triggers',
    "--result-file=$partial", $DatabaseName)
& $MysqldumpPath @argsForDump
if ($LASTEXITCODE -ne 0) { throw "Export failed. Do not import the partial file: $partial" }
if (-not (Test-Path -LiteralPath $partial) -or (Get-Item -LiteralPath $partial).Length -eq 0) {
    throw 'Export is missing or empty.'
}
Move-Item -LiteralPath $partial -Destination $dump
$sha = (Get-FileHash -LiteralPath $dump -Algorithm SHA256).Hash
[IO.File]::WriteAllText("$dump.sha256", "$sha  $([IO.Path]::GetFileName($dump))" + [Environment]::NewLine, [Text.Encoding]::ASCII)
Write-Host "Export completed: $dump"
Write-Host "SHA256: $sha"
return $dump
