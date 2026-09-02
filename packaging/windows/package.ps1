$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$jar = Join-Path $projectRoot 'target\rollcall.jar'
$output = Join-Path $PSScriptRoot 'dist'

if (-not (Test-Path $jar)) {
    throw "Executable JAR not found: $jar"
}

if (Test-Path $output) {
    Remove-Item -Recurse -Force $output
}
New-Item -ItemType Directory -Path $output | Out-Null

$common = @(
    '--input', (Split-Path -Parent $jar),
    '--main-jar', (Split-Path -Leaf $jar),
    '--main-class', 'com.dai2010.rollcall.Main',
    '--name', 'RollCall',
    '--app-version', '1.0.0',
    '--vendor', 'Dai2010',
    '--description', 'Java desktop roll-call tool',
    '--license-file', (Join-Path $projectRoot 'LICENSE'),
    '--icon', (Join-Path $projectRoot 'src\main\resources\icons\rollcall-icon.ico'),
    '--win-menu',
    '--win-shortcut'
)

& jpackage @common '--type' 'exe' '--dest' $output
& jpackage @common '--type' 'msi' '--dest' $output

Get-ChildItem $output | Format-Table Name, Length
