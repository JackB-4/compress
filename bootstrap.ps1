$ErrorActionPreference = 'Stop'
$tools = Join-Path $PSScriptRoot '.tools'
New-Item -ItemType Directory -Force $tools | Out-Null
function Get-Tool($Name, $Url, $Sha) {
    $archive = Join-Path $tools "$Name.zip"
    if (!(Test-Path $archive)) { Invoke-WebRequest -Uri $Url -OutFile $archive }
    if ((Get-FileHash $archive -Algorithm SHA256).Hash -ne $Sha) { throw "Checksum mismatch: $Name" }
    $destination = Join-Path $tools $Name
    if (!(Test-Path $destination)) { Expand-Archive -LiteralPath $archive -DestinationPath $destination }
}
Get-Tool 'jdk' 'https://aka.ms/download-jdk/microsoft-jdk-17.0.17-windows-x64.zip' '7BF59E0BAB043C1869303AFF89CE9C76982561EC9A0D74AE868AC65468822466'
Get-Tool 'sdk' 'https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip' '98B565CB657B012DAE6794CEFC0F66AE1EFB4690C699B78A614B4A6A3505B003'
$env:JAVA_HOME = (Get-ChildItem "$tools/jdk" -Directory | Select-Object -First 1).FullName
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
& "$tools/sdk/cmdline-tools/bin/sdkmanager.bat" "--sdk_root=$tools/sdk" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
if ($LASTEXITCODE -ne 0) { throw 'SDK setup failed' }
$sdkPath = "$tools/sdk".Replace('\','/').Replace(':','\:')
Set-Content -Path "$PSScriptRoot/local.properties" -Value "sdk.dir=$sdkPath" -Encoding ascii
