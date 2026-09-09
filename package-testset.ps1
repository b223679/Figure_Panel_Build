$ErrorActionPreference = 'Stop'
$taskSampleFiles = @('example-settings.json', 'Image A.tif', 'Image B.tif', 'Image C.tif') |
    ForEach-Object { Join-Path $PSScriptRoot "test-data/$_" }
foreach ($taskSampleFile in $taskSampleFiles) {
    if (-not (Test-Path -LiteralPath $taskSampleFile -PathType Leaf)) {
        throw "Missing sample file: $taskSampleFile"
    }
}
$taskDist = Join-Path $PSScriptRoot 'dist'
New-Item -ItemType Directory -Path $taskDist -Force | Out-Null
Compress-Archive -LiteralPath $taskSampleFiles -DestinationPath (Join-Path $taskDist 'figure_panel_builder_testset.zip') -Force
