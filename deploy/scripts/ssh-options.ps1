function Get-DeveopsjSshOptions {
    param(
        [Parameter(Mandatory)][string]$IdentityFile,
        [string]$KnownHostsFile
    )
    if (-not (Test-Path -LiteralPath $IdentityFile -PathType Leaf)) {
        throw 'SSH private key is missing. Copy your existing key from the home PC; do not put it in Git.'
    }
    $identityPath = (Resolve-Path -LiteralPath $IdentityFile).Path
    $options = @('-i', $identityPath, '-o', 'IdentitiesOnly=yes',
        '-o', 'ConnectTimeout=10', '-o', 'ServerAliveInterval=30',
        '-o', 'ServerAliveCountMax=3')
    if ($KnownHostsFile) {
        if (-not (Test-Path -LiteralPath $KnownHostsFile -PathType Leaf)) {
            throw 'The supplied known-hosts file does not exist.'
        }
        $knownHostsPath = (Resolve-Path -LiteralPath $KnownHostsFile).Path.Replace('\', '/')
        $options += @('-o', ('UserKnownHostsFile="{0}"' -f $knownHostsPath), '-o', 'StrictHostKeyChecking=yes')
    } else {
        $options += @('-o', 'StrictHostKeyChecking=ask')
    }
    return $options
}
