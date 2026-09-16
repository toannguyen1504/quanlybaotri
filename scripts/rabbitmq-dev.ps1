[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('start', 'status', 'stop')]
    [string]$Command = 'status',

    [string]$Distribution = 'Ubuntu',

    [ValidateRange(1, 60)]
    [int]$TimeoutSeconds = 20,

    [string]$DevUsername = 'maintenance_app',

    [string]$DevPassword = 'MaintenanceRabbit@123',

    [string]$DevVirtualHost = 'quanlybaotri'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$RabbitHost = '127.0.0.1'
$AmqpPort = 5672
$ManagementPort = 15672
$KeepAliveStateFile = Join-Path ([IO.Path]::GetTempPath()) 'quanlybaotri-rabbitmq-wsl.pid'

function Stop-WithMessage {
    param([string]$Message)

    Write-Host "ERROR: $Message" -ForegroundColor Red
    exit 1
}

function Test-WslDistribution {
    $distributions = @(& wsl.exe --list --quiet 2>$null) |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ }

    return $distributions -contains $Distribution
}

function Invoke-WslCommand {
    param(
        [string[]]$Arguments,
        [switch]$AllowFailure
    )

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $rawOutput = @(& wsl.exe -d $Distribution -u root -- @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    $output = @($rawOutput | Where-Object {
            $_.ToString() -notmatch "^wsl: Failed to translate '.*'$" -and
            $_.ToString() -notmatch '^wsl: The wsl2\.localhostForwarding setting has no effect'
        })
    if (-not $AllowFailure -and $exitCode -ne 0) {
        $details = ($output | Out-String).Trim()
        Stop-WithMessage "WSL command failed (exit $exitCode). $details"
    }

    return [pscustomobject]@{
        ExitCode = $exitCode
        Output   = $output
    }
}

function Get-KeepAliveProcess {
    if (-not (Test-Path -LiteralPath $KeepAliveStateFile)) {
        return $null
    }

    $savedPid = 0
    if (-not [int]::TryParse((Get-Content -LiteralPath $KeepAliveStateFile -Raw).Trim(), [ref]$savedPid)) {
        Remove-Item -LiteralPath $KeepAliveStateFile -Force
        return $null
    }

    $process = Get-Process -Id $savedPid -ErrorAction SilentlyContinue
    if ($null -eq $process -or $process.ProcessName -ne 'wsl') {
        Remove-Item -LiteralPath $KeepAliveStateFile -Force
        return $null
    }

    return $process
}

function Start-WslKeepAlive {
    if ($null -ne (Get-KeepAliveProcess)) {
        return
    }

    $process = Start-Process -FilePath 'wsl.exe' -ArgumentList @(
        '-d', $Distribution, '-u', 'root', '--', '/usr/bin/tail', '-f', '/dev/null'
    ) -WindowStyle Hidden -PassThru
    Set-Content -LiteralPath $KeepAliveStateFile -Value $process.Id -Encoding ASCII
    Start-Sleep -Milliseconds 750

    if ($process.HasExited) {
        Remove-Item -LiteralPath $KeepAliveStateFile -Force -ErrorAction SilentlyContinue
        Stop-WithMessage 'WSL keep-alive process exited before RabbitMQ could start.'
    }
}

function Stop-WslKeepAlive {
    $process = Get-KeepAliveProcess
    if ($null -ne $process) {
        Stop-Process -Id $process.Id -Force
    }
    Remove-Item -LiteralPath $KeepAliveStateFile -Force -ErrorAction SilentlyContinue
}

function Test-RabbitPing {
    $result = Invoke-WslCommand -Arguments @('rabbitmq-diagnostics', '-q', 'ping') -AllowFailure
    return $result.ExitCode -eq 0
}

function Test-WindowsPort {
    param([int]$Port)

    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $connect = $client.ConnectAsync($RabbitHost, $Port)
        return $connect.Wait(2000) -and $client.Connected
    }
    catch {
        return $false
    }
    finally {
        $client.Dispose()
    }
}

function Test-ManagementApi {
    $client = [System.Net.Http.HttpClient]::new()
    try {
        $client.Timeout = [TimeSpan]::FromSeconds(3)
        $credentials = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${DevUsername}:${DevPassword}"))
        $client.DefaultRequestHeaders.Authorization =
            [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Basic', $credentials)
        $response = $client.GetAsync("http://${RabbitHost}:${ManagementPort}/api/overview").GetAwaiter().GetResult()
        return $response.IsSuccessStatusCode
    }
    catch {
        return $false
    }
    finally {
        $client.Dispose()
    }
}

function Initialize-DevAccount {
    Invoke-WslCommand -Arguments @('rabbitmq-plugins', 'enable', 'rabbitmq_management') | Out-Null

    $vhosts = Invoke-WslCommand -Arguments @('rabbitmqctl', '-q', 'list_vhosts', 'name')
    if (-not ($vhosts.Output | Where-Object { $_.ToString().Trim() -eq $DevVirtualHost })) {
        Invoke-WslCommand -Arguments @('rabbitmqctl', 'add_vhost', $DevVirtualHost) | Out-Null
    }

    $users = Invoke-WslCommand -Arguments @('rabbitmqctl', '-q', 'list_users')
    $escapedUsername = [Regex]::Escape($DevUsername)
    if ($users.Output | Where-Object { $_.ToString().Trim() -match "^${escapedUsername}(\s|$)" }) {
        Invoke-WslCommand -Arguments @('rabbitmqctl', 'change_password', $DevUsername, $DevPassword) | Out-Null
    }
    else {
        Invoke-WslCommand -Arguments @('rabbitmqctl', 'add_user', $DevUsername, $DevPassword) | Out-Null
    }

    Invoke-WslCommand -Arguments @('rabbitmqctl', 'set_permissions', '-p', $DevVirtualHost, $DevUsername,
        '.*', '.*', '.*') | Out-Null
    Invoke-WslCommand -Arguments @('rabbitmqctl', 'set_user_tags', $DevUsername, 'management') | Out-Null
}

if (-not (Get-Command wsl.exe -ErrorAction SilentlyContinue)) {
    Stop-WithMessage 'WSL is not installed. Run: wsl.exe --install -d Ubuntu'
}

if (-not (Test-WslDistribution)) {
    Stop-WithMessage "WSL distribution '$Distribution' is not installed. Run: wsl.exe --install -d $Distribution"
}

$rabbitDiagnostics = Invoke-WslCommand -Arguments @('sh', '-lc', 'command -v rabbitmq-diagnostics >/dev/null') -AllowFailure
if ($rabbitDiagnostics.ExitCode -ne 0) {
    Stop-WithMessage "RabbitMQ is not installed in '$Distribution'. Follow the RabbitMQ bootstrap steps in README.md."
}

switch ($Command) {
    'start' {
        Start-WslKeepAlive
        $start = Invoke-WslCommand -Arguments @('systemctl', 'start', 'rabbitmq-server') -AllowFailure
        if ($start.ExitCode -ne 0) {
            Stop-WslKeepAlive
            $details = ($start.Output | Out-String).Trim()
            Stop-WithMessage "Could not start rabbitmq-server. Ensure systemd is enabled in /etc/wsl.conf. $details"
        }

        $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
        while ([DateTime]::UtcNow -lt $deadline -and -not (Test-RabbitPing)) {
            Start-Sleep -Milliseconds 500
        }
        if (-not (Test-RabbitPing)) {
            Stop-WithMessage "RabbitMQ did not become ready within $TimeoutSeconds seconds."
        }

        Initialize-DevAccount

        $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
        while ([DateTime]::UtcNow -lt $deadline -and
            (-not (Test-WindowsPort -Port $AmqpPort) -or -not (Test-ManagementApi))) {
            Start-Sleep -Milliseconds 500
        }
        if (-not (Test-WindowsPort -Port $AmqpPort)) {
            Stop-WithMessage "RabbitMQ is running in WSL, but Windows cannot reach localhost:$AmqpPort."
        }
        if (-not (Test-ManagementApi)) {
            Stop-WithMessage "RabbitMQ Management is not reachable or dev authentication failed on localhost:$ManagementPort."
        }

        Write-Host "RabbitMQ is ready: AMQP localhost:$AmqpPort, management http://localhost:$ManagementPort, vhost '$DevVirtualHost'." -ForegroundColor Green
    }

    'status' {
        $service = Invoke-WslCommand -Arguments @('systemctl', 'is-active', 'rabbitmq-server') -AllowFailure
        $serviceState = ($service.Output | Out-String).Trim()
        $pingOk = Test-RabbitPing
        $amqpOk = Test-WindowsPort -Port $AmqpPort
        $managementOk = Test-ManagementApi
        $keepAlive = Get-KeepAliveProcess

        Write-Host "rabbitmq-server service: $serviceState"
        Write-Host "RabbitMQ node:           $(if ($pingOk) { 'READY' } else { 'FAILED' })"
        Write-Host "AMQP from Windows:       $(if ($amqpOk) { 'READY' } else { 'FAILED' })"
        Write-Host "Management API:          $(if ($managementOk) { 'READY' } else { 'FAILED' })"
        Write-Host "WSL keep-alive:          $(if ($null -ne $keepAlive) { 'RUNNING' } else { 'STOPPED' })"

        if ($service.ExitCode -ne 0 -or -not $pingOk -or -not $amqpOk -or -not $managementOk -or
            $null -eq $keepAlive) {
            exit 1
        }
    }

    'stop' {
        Invoke-WslCommand -Arguments @('systemctl', 'stop', 'rabbitmq-server') | Out-Null
        Stop-WslKeepAlive

        $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
        while ([DateTime]::UtcNow -lt $deadline -and (Test-WindowsPort -Port $AmqpPort)) {
            Start-Sleep -Milliseconds 500
        }
        if (Test-WindowsPort -Port $AmqpPort) {
            Stop-WithMessage "rabbitmq-server stopped, but another service still listens on localhost:$AmqpPort."
        }

        Write-Host 'RabbitMQ is stopped and localhost:5672 is closed.' -ForegroundColor Green
    }
}
