param(
    [switch] $SkipBuild,
    [ValidateRange(0, 2147483647)] [int] $BStatsPluginId = 34825
)

$ErrorActionPreference = 'Stop'
if ($SkipBuild -and $PSBoundParameters.ContainsKey('BStatsPluginId')) {
    throw 'Pass -BStatsPluginId without -SkipBuild so the ID is embedded in every JAR.'
}
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$pluginVersion = '1.2.0'
$release = Join-Path $repo "release-server-$pluginVersion"
$branches = @(
    @{ Project = '1.17.x/project'; Family = '1.17.x'; Platforms = 'Paper-Purpur' },
    @{ Project = '1.18.x/project'; Family = '1.18.x'; Platforms = 'Paper-Purpur' },
    @{ Project = '1.19.x/project'; Family = '1.19.x'; Platforms = 'Paper-Purpur-Folia' },
    @{ Project = '1.20.0-1.20.4/project'; Family = '1.20.0-1.20.4'; Platforms = 'Paper-Purpur-Folia' },
    @{ Project = '1.20.x/project'; Family = '1.20.5-1.20.6'; Platforms = 'Paper-Purpur-Folia' },
    @{ Project = '.'; Family = '1.21-26.3'; Platforms = 'Paper-Purpur-Folia' }
)

foreach ($branch in $branches) {
    $project = Join-Path $repo $branch.Project
    if (-not $SkipBuild) {
        Push-Location $project
        try {
            & (Join-Path $project 'gradlew.bat') build --no-daemon "-PbstatsPluginId=$BStatsPluginId"
            if ($LASTEXITCODE -ne 0) { throw "Gradle build failed for $($branch.Project)" }
        } finally {
            Pop-Location
        }
    }
    foreach ($platform in @($branch.Platforms, 'Spigot-Bukkit')) {
        $name = "WorldHeightLimit-v$pluginVersion-$platform-$($branch.Family).jar"
        $source = Join-Path $project "build/libs/$name"
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
            throw "Missing build: $source"
        }
    }
}

New-Item -ItemType Directory -Force -Path $release | Out-Null
Get-ChildItem -LiteralPath $release -File -Filter '*.jar' | ForEach-Object {
    Remove-Item -LiteralPath $_.FullName
}
foreach ($branch in $branches) {
    $project = Join-Path $repo $branch.Project
    $familyFolder = Join-Path $release $branch.Family
    New-Item -ItemType Directory -Force -Path $familyFolder | Out-Null
    Get-ChildItem -LiteralPath $familyFolder -File -Filter '*.jar' | ForEach-Object {
        Remove-Item -LiteralPath $_.FullName
    }
    foreach ($platform in @($branch.Platforms, 'Spigot-Bukkit')) {
        $name = "WorldHeightLimit-v$pluginVersion-$platform-$($branch.Family).jar"
        Copy-Item -LiteralPath (Join-Path $project "build/libs/$name") -Destination (Join-Path $familyFolder $name)
    }
}
Get-ChildItem -LiteralPath $release -Directory | Sort-Object Name | ForEach-Object {
    Get-ChildItem -LiteralPath $_.FullName -File -Filter '*.jar' | Sort-Object Name | Select-Object @{Name='VersionFolder';Expression={$_.Directory.Name}}, Name, Length
}
