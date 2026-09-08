param([string]$Goal = 'verify')
$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Fiji\java\win64\zulu21.42.19-ca-jdk21.0.7-win_x64' }
$taskMaven = "$PSScriptRoot\.tools\apache-maven-3.9.9\bin\mvn.cmd"
if (-not (Test-Path -LiteralPath $taskMaven)) { $taskMaven = 'mvn.cmd' }
& $taskMaven '-B' '-ntp' '-f' "$PSScriptRoot\pom.xml" "-Dmaven.repo.local=$PSScriptRoot\.tools\repository" $Goal
if ($LASTEXITCODE -ne 0) { throw "Maven failed: $LASTEXITCODE" }
