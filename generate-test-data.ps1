$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\build.ps1" -Goal test-compile
$taskJava = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'C:\Program Files\Fiji\java\win64\zulu21.42.19-ca-jdk21.0.7-win_x64\bin\java.exe' }
Push-Location $PSScriptRoot
try {
    & $taskJava '-Djava.awt.headless=false' '-cp' 'target/test-classes;target/classes;C:\Program Files\Fiji\jars\*' org.microscopy.figure.DeliverableGenerator
    if ($LASTEXITCODE -ne 0) { throw 'Test data generation failed.' }
} finally { Pop-Location }
