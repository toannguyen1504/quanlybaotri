[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('start', 'status', 'stop')]
    [string]$Command = 'status',

    [string]$Distribution = 'Ubuntu',

    [ValidateRange(1, 60)]
    [int]$TimeoutSeconds = 15
)

$ErrorActionPreference = 'Stop'
$RedisHost = '127.0.0.1'
$RedisPort = 6379
$KeepAliveStateFile = Join-Path ([IO.Path]::GetTempPath()) 'quanlybaotri-redis-wsl.pid'

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

    # Windows PowerShell promotes native stderr to an ErrorRecord when
    # ErrorActionPreference is Stop. WSL can also emit harmless PATH translation
    # warnings before otherwise successful commands, so capture and filter those
    # without weakening exit-code checks for real failures.
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
            $_.ToString() -notmatch "^wsl: Failed to translate '.*'$"
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

function Test-RedisPing {
    $result = Invoke-WslCommand -Arguments @(
        'redis-cli', '-h', $RedisHost, '-p', "$RedisPort", '--raw', 'PING'
    ) -AllowFailure

    return $result.ExitCode -eq 0 -and (($result.Output | Out-String).Trim() -eq 'PONG')
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
    $existing = Get-KeepAliveProcess
    if ($null -ne $existing) {
        return
    }

    $process = Start-Process -FilePath 'wsl.exe' -ArgumentList @(
        '-d', $Distribution, '-u', 'root', '--', '/usr/bin/tail', '-f', '/dev/null'
    ) -WindowStyle Hidden -PassThru
    Set-Content -LiteralPath $KeepAliveStateFile -Value $process.Id -Encoding ASCII
    Start-Sleep -Milliseconds 750

    if ($process.HasExited) {
        Remove-Item -LiteralPath $KeepAliveStateFile -Force -ErrorAction SilentlyContinue
        Stop-WithMessage 'WSL keep-alive process exited before Redis could start.'
    }
}

function Stop-WslKeepAlive {
    $process = Get-KeepAliveProcess
    if ($null -ne $process) {
        Stop-Process -Id $process.Id -Force
    }
    Remove-Item -LiteralPath $KeepAliveStateFile -Force -ErrorAction SilentlyContinue
}

function Test-WindowsRedisPing {
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $client.ReceiveTimeout = 2000
        $client.SendTimeout = 2000
        $client.Connect($RedisHost, $RedisPort)
        $stream = $client.GetStream()
        $request = [Text.Encoding]::ASCII.GetBytes("*1`r`n`$4`r`nPING`r`n")
        $stream.Write($request, 0, $request.Length)
        $buffer = [byte[]]::new(64)
        $count = $stream.Read($buffer, 0, $buffer.Length)
        $response = [Text.Encoding]::ASCII.GetString($buffer, 0, $count).Trim()
        return $response -eq '+PONG'
    }
    catch {
        return $false
    }
    finally {
        $client.Dispose()
    }
}

if (-not (Get-Command wsl.exe -ErrorAction SilentlyContinue)) {
    Stop-WithMessage 'WSL is not installed. Run: wsl.exe --install -d Ubuntu'
}

if (-not (Test-WslDistribution)) {
    Stop-WithMessage "WSL distribution '$Distribution' is not installed. Run: wsl.exe --install -d $Distribution"
}

$redisCli = Invoke-WslCommand -Arguments @('sh', '-lc', 'command -v redis-cli >/dev/null') -AllowFailure
if ($redisCli.ExitCode -ne 0) {
    Stop-WithMessage "redis-cli is not installed in '$Distribution'. Follow the Redis bootstrap steps in README.md."
}

switch ($Command) {
    'start' {
        Start-WslKeepAlive
        $start = Invoke-WslCommand -Arguments @('systemctl', 'start', 'redis-server') -AllowFailure
        if ($start.ExitCode -ne 0) {
            Stop-WslKeepAlive
            $details = ($start.Output | Out-String).Trim()
            Stop-WithMessage "Could not start redis-server. Ensure systemd is enabled in /etc/wsl.conf. $details"
        }

        $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
        while ([DateTime]::UtcNow -lt $deadline -and -not (Test-RedisPing)) {
            Start-Sleep -Milliseconds 500
        }

        if (-not (Test-RedisPing)) {
            Stop-WithMessage "Redis did not return PONG within $TimeoutSeconds seconds."
        }
        if (-not (Test-WindowsRedisPing)) {
            Stop-WithMessage 'Redis is running in WSL, but Windows did not receive PONG from localhost:6379. Check localhostForwarding/networkingMode in %UserProfile%\.wslconfig, then run wsl.exe --shutdown.'
        }

        Write-Host 'Redis is ready: PONG from both WSL and Windows localhost:6379.' -ForegroundColor Green
    }

    'status' {
        $service = Invoke-WslCommand -Arguments @('systemctl', 'is-active', 'redis-server') -AllowFailure
        $serviceState = ($service.Output | Out-String).Trim()
        $pingOk = Test-RedisPing
        $windowsPingOk = Test-WindowsRedisPing
        $keepAlive = Get-KeepAliveProcess

        Write-Host "redis-server service: $serviceState"
        Write-Host "Redis PING in WSL:    $(if ($pingOk) { 'PONG' } else { 'FAILED' })"
        Write-Host "Redis PING in Windows: $(if ($windowsPingOk) { 'PONG' } else { 'FAILED' })"
        Write-Host "WSL keep-alive:        $(if ($null -ne $keepAlive) { 'RUNNING' } else { 'STOPPED' })"

        if ($service.ExitCode -ne 0 -or -not $pingOk -or -not $windowsPingOk -or $null -eq $keepAlive) {
            exit 1
        }
    }

    'stop' {
        Invoke-WslCommand -Arguments @('systemctl', 'stop', 'redis-server') | Out-Null
        Stop-WslKeepAlive

        $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
        while ([DateTime]::UtcNow -lt $deadline -and (Test-WindowsRedisPing)) {
            Start-Sleep -Milliseconds 500
        }

        if (Test-WindowsRedisPing) {
            Stop-WithMessage "redis-server stopped, but another Redis still responds on localhost:$RedisPort."
        }

        Write-Host 'Redis is stopped and localhost:6379 is closed.' -ForegroundColor Green
    }
}
