<#
  Launch the RPClient AVD and pin its window inside the visible desktop.

  Why this exists: the emulator keeps its last window geometry and sizes the
  window from the device's pixel height. On a 2560x1440 screen at 175% scaling
  the client area ends up taller than the desktop, so the placement algorithm
  pushes most of the window above the top edge and it looks stuck in the
  upper-right corner. This script waits for the window, then forces it to fit
  the current monitor and centers it.

  Usage:
    pwsh -File run-emulator.ps1
    pwsh -File run-emulator.ps1 -Apk '..\dist\rpclient-2026.3.1-dev-debug.apk'
#>
param(
    [string]$Avd = 'RPClient_API36',
    [string]$Apk = '',
    [string]$Sdk = '',
    [int]$Margin = 40,
    # Quick Boot snapshots go stale after a forced shutdown and the guest then
    # hangs before adbd starts. A cold boot reaches sys.boot_completed in about
    # 15s here, so skip the snapshot unless it is explicitly requested.
    [bool]$ColdBoot = $true,
    # 客户机显示尺寸：AVD 默认 1080x2400 会让窗口高过宿主屏幕，底部导航栏落到桌面外。
    [string]$Resolution = '900x1600',
    [int]$Density = 420,
    # 图形后端。默认走软件渲染：qemu 抢独显时驱动死锁过，
    # 表现是整机僵住且不报蓝屏，只能长按电源。
    [string]$Gpu = 'swiftshader_indirect',
    # 客户机内存上限。给太多没意义，给太少起不来，2 GB 足够跑这个应用。
    [int]$MemoryMb = 2048,
    # 低于这个可用内存就不启动，免得和构建、浏览器一起把机器拖死。
    [int]$MinFreeGb = 6
)

# adb prints routine state transitions to stderr, so native failures must not
# abort the script; real problems are reported with explicit throw statements.
$ErrorActionPreference = 'Continue'

# 起模拟器前先看机器还剩多少：这次死机的教训是没看负载就开。
$osInfo = Get-CimInstance Win32_OperatingSystem
$freeGb = [math]::Round($osInfo.FreePhysicalMemory / 1MB, 1)
if ($freeGb -lt $MinFreeGb) {
    throw "可用内存只有 ${freeGb} GB，低于阈值 ${MinFreeGb} GB。先关掉占内存的程序，或用 -MinFreeGb 调低阈值。"
}
# 构建和模拟器同时跑是死机的典型组合，提醒一句而不是直接拦住。
$busyJava = Get-Process -Name 'java' -ErrorAction SilentlyContinue
if ($busyJava) {
    Write-Warning "检测到 java 进程（可能是 Gradle 构建）。构建与模拟器同时跑容易拖死系统，建议分开。"
}
"free memory ${freeGb} GB, launching with -gpu $Gpu -memory $MemoryMb"

# SDK discovery order: explicit argument, env vars, repo local.properties, then the
# default install location. local.properties is what Gradle uses, so trust it first.
if (-not $Sdk) {
    if ($env:ANDROID_SDK_ROOT) { $Sdk = $env:ANDROID_SDK_ROOT }
    elseif ($env:ANDROID_HOME) { $Sdk = $env:ANDROID_HOME }
}
if (-not $Sdk) {
    $localProps = Join-Path (Split-Path -Parent $PSScriptRoot) 'local.properties'
    if (Test-Path $localProps) {
        $line = Get-Content $localProps | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($line) {
            $value = $line -replace '^sdk\.dir=', ''
            $Sdk = ($value -replace '\\\\', '\') -replace '\\:', ':'
        }
    }
}
if (-not $Sdk -and $env:LOCALAPPDATA) {
    $Sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
}

$emulator = Join-Path $Sdk 'emulator\emulator.exe'
$adb = Join-Path $Sdk 'platform-tools\adb.exe'
if (-not (Test-Path $emulator)) { throw "emulator not found: $emulator" }

if (-not ('EmuWindow' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public class EmuWindow {
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern int GetSystemMetrics(int index);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);
    [DllImport("user32.dll")] public static extern bool MoveWindow(IntPtr hWnd, int x, int y, int width, int height, bool repaint);
    public struct RECT { public int Left; public int Top; public int Right; public int Bottom; }
}
'@
}
[EmuWindow]::SetProcessDPIAware() | Out-Null

# Reuse an already running emulator window instead of starting a second one.
$qemu = Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne 0 }
if (-not $qemu) {
    $emuArgs = @('-avd', $Avd)
    if ($ColdBoot) { $emuArgs += '-no-snapshot-load' }
    if ($Gpu) { $emuArgs += @('-gpu', $Gpu) }
    if ($MemoryMb -gt 0) { $emuArgs += @('-memory', "$MemoryMb") }
    # 音频子系统对调试没用，少一个驱动就少一处死锁可能
    $emuArgs += '-no-audio'
    Start-Process -FilePath $emulator -ArgumentList $emuArgs -WorkingDirectory (Join-Path $Sdk 'emulator') | Out-Null
    $deadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Milliseconds 700
        $qemu = Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne 0 }
        if ($qemu) { break }
    }
}
if (-not $qemu) { throw 'emulator window did not appear within 90s' }
$handle = $qemu[0].MainWindowHandle

$screenW = [EmuWindow]::GetSystemMetrics(0)
$screenH = [EmuWindow]::GetSystemMetrics(1)

# Shrink the window until it fits the usable height, keeping the aspect ratio.
$rect = New-Object EmuWindow+RECT
[EmuWindow]::GetWindowRect($handle, [ref]$rect) | Out-Null
$width = $rect.Right - $rect.Left
$height = $rect.Bottom - $rect.Top
$maxH = $screenH - (2 * $Margin)
if ($height -gt $maxH) {
    $scale = $maxH / $height
    $width = [int]($width * $scale)
    $height = $maxH
}
$width = [Math]::Min($width, $screenW - (2 * $Margin))

$x = [int](($screenW - $width) / 2)
$y = [int](($screenH - $height) / 2)

# 一次定位不够：qemu 启动过程中 MainWindowHandle 会变，
# 早期那个句柄移完，真正的主窗口随后又按记忆的位置摆回屏幕外（Top 能到负几百）。
# 所以连着盯几秒，位置不对就再拉一次，直到它不再自己动。
function Get-EmulatorWindow {
    Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowHandle -ne 0 } |
        Select-Object -First 1
}
$stableCount = 0
for ($i = 0; $i -lt 40; $i++) {
    $proc = Get-EmulatorWindow
    if (-not $proc) { break }
    $h = $proc.MainWindowHandle
    $probe = New-Object EmuWindow+RECT
    [EmuWindow]::GetWindowRect($h, [ref]$probe) | Out-Null
    $offscreen = $probe.Top -lt 0 -or $probe.Left -lt 0 -or
        $probe.Right -gt $screenW -or $probe.Bottom -gt $screenH
    if ($offscreen) {
        [EmuWindow]::MoveWindow($h, $x, $y, $width, $height, $true) | Out-Null
        $stableCount = 0
    } else {
        $stableCount++
        # 连续 6 次（约 3 秒）都没跑偏才算定住
        if ($stableCount -ge 6) { break }
    }
    Start-Sleep -Milliseconds 500
}
$finalRect = New-Object EmuWindow+RECT
[EmuWindow]::GetWindowRect((Get-EmulatorWindow).MainWindowHandle, [ref]$finalRect) | Out-Null
"window pinned at ($($finalRect.Left),$($finalRect.Top)) ${width}x${height} on ${screenW}x${screenH}"

# Wait for the guest to finish booting before optionally installing the APK.
# adb reports "offline" while the guest is still coming up, so ask for the boot
# property and treat any failure as "not ready yet". Parsing `adb devices` is
# avoided on purpose: its line endings carry a CR that breaks an end-anchored
# regex on Windows.
& { & $adb start-server 2>&1 | Out-Null }
$booted = $false
$deadline = (Get-Date).AddSeconds(240)
while ((Get-Date) -lt $deadline) {
    $state = (& $adb shell getprop sys.boot_completed 2>&1 | Out-String).Trim()
    if ($state -eq '1') { $booted = $true; break }
    Start-Sleep -Seconds 3
}
if (-not $booted) {
    Write-Warning 'guest did not report boot_completed in time; skipping install'
    return
}
"guest booted"

# Keep the guest short enough that the whole window fits on a 1440p screen:
# at the default 2400px height the window overflows and the navigation bar
# ends up below the desktop, which makes manual debugging impossible.
if ($Resolution) {
    & $adb shell wm size $Resolution 2>&1 | Out-Null
    if ($Density) { & $adb shell wm density $Density 2>&1 | Out-Null }
    "resolution set to $Resolution (density $Density)"
    Start-Sleep -Seconds 2
}

if ($Apk) {
    $apkPath = (Resolve-Path -LiteralPath $Apk).Path
    & $adb install -r $apkPath
    & $adb shell monkey -p me.kafuuneko.rpclient.dev -c android.intent.category.LAUNCHER 1 2>&1 | Out-Null
    "installed $apkPath"
}
