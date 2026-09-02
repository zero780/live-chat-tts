$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$sourceRoot = Join-Path $projectRoot 'src\main\java'
$outputRoot = Join-Path $projectRoot 'out'

New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$sources = Get-ChildItem -Path $sourceRoot -Filter '*.java' -Recurse | ForEach-Object FullName
if (-not $sources) { throw 'No se encontraron fuentes Java.' }

javac --release 21 -encoding UTF-8 -d $outputRoot $sources
java -cp $outputRoot com.comext.livechattts.bootstrap.Application

