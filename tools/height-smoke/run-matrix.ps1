param(
    [string[]] $Versions = @('1.17', '1.17.1', '1.18', '1.18.1', '1.18.2', '1.19.3', '1.19.4', '1.20.1', '1.20.2', '1.20.4', '1.20.5', '1.20.6', '1.21.1', '1.21.3', '1.21.4', '1.21.5', '1.21.6', '1.21.8', '1.21.10', '1.21.11', '26.1.2', '26.2', '26.3'),
    [Parameter(Mandatory)] [string] $Java16,
    [Parameter(Mandatory)] [string] $Java17,
    [Parameter(Mandatory)] [string] $Java21,
    [Parameter(Mandatory)] [string] $Java25,
    [string] $SmokeRoot,
    [string] $Vanilla118Jar
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if (-not $SmokeRoot) { $SmokeRoot = Join-Path $repo 'work/height-smoke' }
$SmokeRoot = [System.IO.Path]::GetFullPath($SmokeRoot)
New-Item -ItemType Directory -Force -Path $SmokeRoot | Out-Null

$runtimes = @{
    '16' = (Resolve-Path -LiteralPath $Java16).Path
    '17' = (Resolve-Path -LiteralPath $Java17).Path
    '21' = (Resolve-Path -LiteralPath $Java21).Path
    '25' = (Resolve-Path -LiteralPath $Java25).Path
}

function Get-TestProfile([string] $Version) {
    if ($Version -match '^1\.17(?:\.|$)') {
        return @{ Jar = '1.17.x/project/build/libs/WorldHeightLimit-v1.2.1-Paper-Purpur-1.17.x.jar'; Java = '16'; Bot = $Version }
    }
    if ($Version -match '^1\.18(?:\.|$)') {
        return @{ Jar = '1.18.x/project/build/libs/WorldHeightLimit-v1.2.1-Paper-Purpur-1.18.x.jar'; Java = '17'; Bot = $Version }
    }
    if ($Version -match '^1\.19(?:\.|$)') {
        # minecraft-data maps 1.19.1 to protocol 759, but its server requires
        # protocol 760, shared with 1.19.2.
        $bot = if ($Version -eq '1.19.1') { '1.19.2' } else { $Version }
        return @{ Jar = '1.19.x/project/build/libs/WorldHeightLimit-v1.2.1-Paper-Purpur-Folia-1.19.x.jar'; Java = '17'; Bot = $bot }
    }
    if ($Version -match '^1\.20\.[0-4]$' -or $Version -eq '1.20') {
        return @{ Jar = '1.20.0-1.20.4/project/build/libs/WorldHeightLimit-v1.2.1-Paper-Purpur-Folia-1.20.0-1.20.4.jar'; Java = '17'; Bot = $Version }
    }
    if ($Version -match '^1\.20\.[56]$') {
        return @{ Jar = '1.20.x/project/build/libs/WorldHeightLimit-v1.2.1-Paper-Purpur-Folia-1.20.5-1.20.6.jar'; Java = '21'; Bot = $Version }
    }
    if ($Version -match '^1\.21(?:\.|$)') {
        # minecraft-data's 1.21.7 alias resolves to the older 1.21 protocol;
        # 1.21.8 carries the matching 1.21.7 protocol number.
        $bot = if ($Version -eq '1.21.7') { '1.21.8' } else { $Version }
        return @{ Jar = 'build/libs/WorldHeightLimit-v1.2.1-Paper-Purpur-Folia-1.21-26.3.jar'; Java = '21'; Bot = $bot }
    }
    if ($Version -match '^26\.[123](?:\.|$)') {
        return @{ Jar = 'build/libs/WorldHeightLimit-v1.2.1-Paper-Purpur-Folia-1.21-26.3.jar'; Java = '25'; Bot = '26.1' }
    }
    throw "No server-plugin test profile for Minecraft $Version"
}

$savedEnvironment = @{}
foreach ($name in @('MC_VERSION', 'BOT_VERSION', 'JAVA_BIN', 'PLUGIN_JAR', 'SMOKE_ROOT', 'ALLOW_EXPERIMENTAL', 'VANILLA_SERVER_JAR', 'ENGINE_PROBE_ONLY', 'OBSERVER_JAR')) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

$summary = [System.Collections.Generic.List[object]]::new()
try {
    foreach ($version in $Versions) {
        $profile = Get-TestProfile $version
        $jar = (Resolve-Path -LiteralPath (Join-Path $repo $profile.Jar)).Path
        $env:MC_VERSION = $version
        $env:BOT_VERSION = $profile.Bot
        $env:JAVA_BIN = $runtimes[$profile.Java]
        $env:PLUGIN_JAR = $jar
        $env:SMOKE_ROOT = $SmokeRoot
        $env:ALLOW_EXPERIMENTAL = '1'
        $env:ENGINE_PROBE_ONLY = $null
        $env:OBSERVER_JAR = $null
        $env:VANILLA_SERVER_JAR = if ($version -eq '1.18' -and $Vanilla118Jar) { (Resolve-Path -LiteralPath $Vanilla118Jar).Path } else { $null }

        Write-Host "Testing Minecraft $version with Java $($profile.Java)..."
        & node (Join-Path $PSScriptRoot 'smoke.js')
        $exitCode = $LASTEXITCODE
        $versionRoot = Join-Path $SmokeRoot $version
        $latestRun = Get-ChildItem -LiteralPath $versionRoot -Directory | Sort-Object LastWriteTime -Descending | Select-Object -First 1
        $reportPath = Join-Path $latestRun.FullName 'report.json'
        $report = Get-Content -LiteralPath $reportPath -Raw | ConvertFrom-Json
        $summary.Add([pscustomobject]@{
            minecraft = $version
            status = $report.status
            paperBuild = $report.paper.build
            paperChannel = $report.paper.channel
            botVersion = $report.botVersion
            warnings = if ($null -eq $report.warnings) { 0 } else { @($report.warnings).Count }
            report = $reportPath
            exitCode = $exitCode
        })
    }
} finally {
    foreach ($name in $savedEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process')
    }
    $summaryPath = Join-Path $SmokeRoot 'matrix-summary.json'
    $summary | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $summaryPath -Encoding utf8
    Write-Host "Matrix summary: $summaryPath"
    $summary | Format-Table minecraft, status, paperBuild, paperChannel, warnings -AutoSize
}

if ($summary.Where({ $_.status -ne 'PASS' }).Count -gt 0) { exit 1 }
