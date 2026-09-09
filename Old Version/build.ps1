param([string]$Goal = 'verify')
$ErrorActionPreference = 'Stop'
if ($Goal -match '(^|\s)clean(\s|$)') {
  # Never let Maven clean follow a junction into shared dependencies or another workspace.
  $taskBuildRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'target'))
  if (Test-Path -LiteralPath $taskBuildRoot) {
    $taskPending = [Collections.Generic.Stack[string]]::new()
    $taskPending.Push($taskBuildRoot)
    while ($taskPending.Count -gt 0) {
      $taskEntry = Get-Item -LiteralPath $taskPending.Pop() -Force
      if ($taskEntry.Attributes -band [IO.FileAttributes]::ReparsePoint) {
        throw "Clean stopped: linked path detected at $($taskEntry.FullName). Use incremental verify."
      }
      if ($taskEntry.PSIsContainer) {
        foreach ($taskChild in Get-ChildItem -LiteralPath $taskEntry.FullName -Force) {
          $taskPending.Push($taskChild.FullName)
        }
      }
    }
  }
}
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Fiji\java\win64\zulu21.42.19-ca-jdk21.0.7-win_x64' }
$taskMaven = "$PSScriptRoot\.tools\apache-maven-3.9.9\bin\mvn.cmd"
if (-not (Test-Path -LiteralPath $taskMaven)) { $taskMaven = 'mvn.cmd' }
& $taskMaven '-B' '-ntp' '-f' "$PSScriptRoot\pom.xml" "-Dmaven.repo.local=$PSScriptRoot\.tools\repository" $Goal
if ($LASTEXITCODE -ne 0) { throw "Maven failed: $LASTEXITCODE" }
