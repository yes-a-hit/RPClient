# 把构建产物复制到 dist 并带上定制版版本号。
#
# 目的：dist 里同时可能存在多份包，仅凭时间戳很难区分新旧，
# 因此文件名固定带上上游版本名与定制版版本，例如
# rpclient-2026.3.1-dev-debug-v0.11.apk。
#
# 用法：
#   pwsh -File tools/publish-apk.ps1
#   pwsh -File tools/publish-apk.ps1 -Install   # 额外安装到当前设备
param(
    [switch]$Install,
    [string]$Adb = ''
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $repoRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) { throw "APK 不存在，请先执行 gradlew assembleDebug：$apk" }

# 从源码读取版本号，避免和关于页显示的版本不一致
# 版本号以 gradle.properties 为准：AppModel 已改成读 BuildConfig，
# 源码里不再有字面量，从那里取会取不到
$props = Get-Content (Join-Path $repoRoot 'gradle.properties')
$uvVersion = ($props | Select-String -Pattern '^uv\.version=(.+)$').Matches.Groups[1].Value.Trim()
if (-not $uvVersion) { throw 'gradle.properties 中找不到 uv.version' }

$gradle = Get-Content (Join-Path $repoRoot 'app\build.gradle.kts')
$versionName = ($gradle | Select-String -Pattern 'versionName\s*=\s*"([^"]+)"' | Select-Object -First 1).Matches.Groups[1].Value
if (-not $versionName) { throw 'build.gradle.kts 中找不到 versionName' }

$distDir = Join-Path $repoRoot 'dist'
New-Item -ItemType Directory -Force -Path $distDir | Out-Null
$target = Join-Path $distDir "rpclient-$versionName-dev-debug-v$uvVersion.apk"
Copy-Item $apk $target -Force

# 旧命名的包一并删掉，避免误装到过期构建
Get-ChildItem $distDir -Filter 'rpclient-*-debug.apk' | Where-Object { $_.Name -notmatch '-v[0-9]' } | ForEach-Object {
    Remove-Item $_.FullName -Force
    "removed legacy $( $_.Name )"
}

$item = Get-Item $target
"published $($item.Name) ({0:N1} MB) at $($item.LastWriteTime)" -f ($item.Length / 1MB)

if ($Install) {
    if (-not $Adb) {
        $sdk = $env:ANDROID_SDK_ROOT
        if (-not $sdk) { $sdk = $env:ANDROID_HOME }
        if (-not $sdk) {
            # 从 local.properties 读取，避免把本机路径写进仓库
            $props = Join-Path $repoRoot 'local.properties'
            if (Test-Path $props) {
                $line = Get-Content $props | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
                if ($line) { $sdk = ($line -replace '^sdk\.dir=', '') -replace '\\\\', '\' -replace '\\:', ':' }
            }
        }
        $Adb = Join-Path $sdk 'platform-tools\adb.exe'
    }
    & $Adb install -r $target
}
