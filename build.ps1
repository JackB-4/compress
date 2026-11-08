$ErrorActionPreference = 'Stop'
$base = $PSScriptRoot
if (Test-Path "$base/.tools/jdk") { $env:JAVA_HOME = (Get-ChildItem "$base/.tools/jdk" -Directory | Select-Object -First 1).FullName }
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
& "$base/gradlew.bat" -p $base assembleRelease lintRelease
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
New-Item -ItemType Directory -Force "$base/dist" | Out-Null
Copy-Item "$base/app/build/outputs/apk/release/app-release.apk" "$base/dist/Compress-1.2.apk"
Copy-Item "$base/dist/Compress-1.2.apk" "$base/Compress.apk"
