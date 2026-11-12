[CmdletBinding(SupportsShouldProcess, ConfirmImpact = 'High')]
param(
    [string]$EnvFile = '.env',
    [string]$BackupDirectory = (Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'VNPTCloud-PJ1\backups\organization'),
    [switch]$AllowReplaceTarget
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$signatureFile = Join-Path $PSScriptRoot 'organization-data-signature.sql'
$trafficStopped = $false
$backupPath = $null
$containerDump = $null

function Invoke-Compose {
    param(
        [Parameter(Mandatory, Position = 0, ValueFromRemainingArguments)]
        [string[]]$Arguments
    )

    & docker compose --env-file $script:resolvedEnvFile @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose failed: $($Arguments -join ' ')"
    }
}

function Invoke-ComposeCapture {
    param(
        [Parameter(Mandatory, Position = 0, ValueFromRemainingArguments)]
        [string[]]$Arguments
    )

    $output = & docker compose --env-file $script:resolvedEnvFile @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose failed: $($Arguments -join ' ')"
    }

    return (($output | ForEach-Object { $_.ToString() }) -join "`n").Trim()
}

function Get-OrganizationSignature {
    param(
        [Parameter(Mandatory)]
        [string]$ConnectionCommand
    )

    $sql = Get-Content -Raw -Encoding utf8 $signatureFile
    $output = $sql | & docker compose --env-file $script:resolvedEnvFile exec -T postgres sh -ec $ConnectionCommand
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not calculate the organization database signature.'
    }

    $lines = $output |
        ForEach-Object { $_.ToString().Trim() } |
        Where-Object { $_ } |
        Sort-Object
    return ($lines -join "`n")
}

function Invoke-TargetSql {
    param(
        [Parameter(Mandatory)]
        [string]$Sql
    )

    $output = $Sql | & docker compose --env-file $script:resolvedEnvFile exec -T postgres sh -ec `
        'PGPASSWORD="$ORGANIZATION_DB_PASSWORD" psql -w -h organization-pgpool -U organization_app -d organization_db -v ON_ERROR_STOP=1 -At'
    if ($LASTEXITCODE -ne 0) {
        throw 'Target organization database query failed.'
    }

    return (($output | ForEach-Object { $_.ToString() }) -join "`n").Trim()
}

Push-Location $repoRoot
try {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'Docker CLI was not found.'
    }
    if (-not (Test-Path -LiteralPath $signatureFile -PathType Leaf)) {
        throw "Signature query was not found: $signatureFile"
    }

    $envFilePath = if ([IO.Path]::IsPathRooted($EnvFile)) {
        $EnvFile
    } else {
        Join-Path $repoRoot $EnvFile
    }
    if (-not (Test-Path -LiteralPath $envFilePath -PathType Leaf)) {
        throw "Environment file was not found: $envFilePath"
    }
    $script:resolvedEnvFile = (Resolve-Path -LiteralPath $envFilePath).Path

    $backupFullPath = [IO.Path]::GetFullPath($BackupDirectory)
    $repoPrefix = $repoRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if ($backupFullPath.Equals($repoRoot, [StringComparison]::OrdinalIgnoreCase) -or
        $backupFullPath.StartsWith($repoPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'BackupDirectory must be outside the repository.'
    }

    Invoke-Compose config --quiet

    $targetDescription = 'organization_db on organization-pgpool'
    if (-not $PSCmdlet.ShouldProcess($targetDescription, 'stop public traffic, back up, restore, validate, and switch to HA')) {
        return
    }

    Invoke-Compose up -d --wait postgres
    $sourceExists = Invoke-ComposeCapture exec -T postgres psql -U postgres -d postgres -Atc `
        "SELECT 1 FROM pg_database WHERE datname = 'organization_db'"
    if ($sourceExists -ne '1') {
        throw 'The source organization_db does not exist in the shared postgres service. A fresh installation does not need cutover.'
    }

    Invoke-Compose up -d --wait organization-pgpool
    $targetTableCount = Invoke-TargetSql -Sql 'SELECT count(*) FROM pg_tables WHERE schemaname=current_schema();'
    $targetHasData = [int]$targetTableCount -gt 0
    if ($targetHasData -and -not $AllowReplaceTarget) {
        throw 'The HA target already contains tables. Re-run with -AllowReplaceTarget only after confirming that replacing the target is intended.'
    }

    Invoke-Compose stop web api-gateway
    $trafficStopped = $true

    $timestamp = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')
    $containerDump = "/tmp/organization-cutover-$timestamp.dump"
    New-Item -ItemType Directory -Path $backupFullPath -Force -Confirm:$false | Out-Null
    $resolvedBackupDirectory = (Resolve-Path -LiteralPath $backupFullPath).Path
    $backupPath = Join-Path $resolvedBackupDirectory "organization-$timestamp.dump"
    if (Test-Path -LiteralPath $backupPath) {
        throw "Backup already exists: $backupPath"
    }

    $dumpCommand = 'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump -w -U postgres -d organization_db -Fc -f {0}' -f $containerDump
    Invoke-Compose exec -T postgres sh -ec $dumpCommand
    $null = Invoke-Compose exec -T postgres pg_restore --list $containerDump
    Invoke-Compose cp "postgres:$containerDump" $backupPath

    $sourceSignature = Get-OrganizationSignature `
        -ConnectionCommand 'PGPASSWORD="$POSTGRES_PASSWORD" psql -w -U postgres -d organization_db -At -F :'
    $backupHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $backupPath).Hash

    Invoke-Compose stop organization-service

    if ($targetHasData) {
        $null = Invoke-TargetSql -Sql 'DROP SCHEMA public CASCADE; CREATE SCHEMA public AUTHORIZATION organization_app;'
    }

    $restoreCommand = 'PGPASSWORD="$ORGANIZATION_HA_POSTGRES_PASSWORD" pg_restore -w -h organization-pgpool -U postgres -d organization_db --clean --if-exists --exit-on-error {0}' -f $containerDump
    Invoke-Compose exec -T postgres sh -ec $restoreCommand
    $null = Invoke-TargetSql -Sql 'ANALYZE;'

    $targetSignature = Get-OrganizationSignature `
        -ConnectionCommand 'PGPASSWORD="$ORGANIZATION_DB_PASSWORD" psql -w -h organization-pgpool -U organization_app -d organization_db -At -F :'

    $manifest = [ordered]@{
        createdAtUtc    = $timestamp
        source          = 'postgres/organization_db'
        target          = 'organization-pgpool/organization_db'
        backupFile      = $backupPath
        backupSha256    = $backupHash
        sourceSignature = $sourceSignature -split "`r?`n"
        targetSignature = $targetSignature -split "`r?`n"
    }
    $manifestPath = Join-Path $resolvedBackupDirectory "organization-$timestamp.manifest.json"
    $manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $manifestPath -Encoding utf8 -Confirm:$false

    if ($sourceSignature -ne $targetSignature) {
        throw "Source and target signatures differ. Traffic remains stopped. Review $manifestPath"
    }

    Invoke-Compose up -d --build --wait organization-service
    $haHealth = Invoke-ComposeCapture exec -T organization-service wget -qO- http://127.0.0.1:8086/ha-health
    if ($haHealth -ne 'UP') {
        throw "HAProxy health check returned '$haHealth'. Traffic remains stopped."
    }

    Invoke-Compose up -d --build --wait api-gateway web
    $trafficStopped = $false

    Write-Host "Organization HA cutover completed."
    Write-Host "Backup: $backupPath"
    Write-Host "Manifest: $manifestPath"
} catch {
    if ($trafficStopped) {
        Write-Warning 'Public traffic is still stopped. Do not resume it until the failure is resolved or the previous Compose revision is restored.'
    }
    if ($backupPath) {
        Write-Warning "Source backup: $backupPath"
    }
    throw
} finally {
    if ($containerDump) {
        & docker compose --env-file $script:resolvedEnvFile exec -T postgres rm -f $containerDump 2>$null
    }
    Pop-Location
}
