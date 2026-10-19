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
    [string]$Sdk = $(if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT }
        elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME }
        elseif ($env:LOCALAPPDATA) { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
        else { '' }),
    [int]$Margin = 40,
    # Quick Boot snapshots go stale after a forced shutdown and the guest then
    # hangs before adbd starts. A cold boot reaches sys.boot_completed in about
    # 15s here, so skip the snapshot unless it is explicitly requested.
    [bool]$ColdBoot = $true
)

# adb prints routine state transitions to stderr, so native failures must not
# abort the script; real problems are reported with explicit throw statements.
$ErrorActionPreference = 'Continue'

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
[EmuWindow]::MoveWindow($handle, $x, $y, $width, $height, $true) | Out-Null
"window pinned at ($x,$y) ${width}x${height} on ${screenW}x${screenH}"

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

if ($Apk) {
    $apkPath = (Resolve-Path -LiteralPath $Apk).Path
    & $adb install -r $apkPath
    & $adb shell monkey -p me.kafuuneko.rpclient.dev -c android.intent.category.LAUNCHER 1 2>&1 | Out-Null
    "installed $apkPath"
}
