# Replace the installed Gokapi Desktop (Windows) with the freshly built app
# image from `gradlew createDistributable`, without running the installer.
# Adapted from metrolist-desktop scripts/update-installed-exe.ps1.
#
# Only the three items jpackage installs are touched: Files - Gokapi Desktop.exe,
# app\ and runtime\ inside the folder the installer registered as
# InstallLocation. The settings, saved credentials, and caches
# (%LOCALAPPDATA%\GokapiDesktop) live elsewhere and are never touched. Runs
# on Windows PowerShell 5.1.
#
# When the app is not installed yet and -Installer is given, runs that
# installer once instead. -Version writes the new version to the uninstall
# entry, so Apps & features shows the build that is now installed. An install
# from before the rename (gokapi-desktop) also runs the installer, which
# replaces it through the shared upgradeUuid.
#
#   scripts\update-installed-exe.ps1 [-Installer path\to\installer.exe] [-Version 0.1.47] [-DryRun]

param([string]$Installer, [string]$Version, [switch]$DryRun)

$ErrorActionPreference = 'Stop'

$repo = Split-Path -Parent $PSScriptRoot
$name = 'Files - Gokapi Desktop'
$legacyName = 'gokapi-desktop'
$source = Join-Path $repo "desktop\build\compose\binaries\main\app\$name"
$items = @("$name.exe", 'app', 'runtime')

function Test-AppLayout([string]$dir) {
    (Test-Path -LiteralPath (Join-Path $dir "$name.exe") -PathType Leaf) -and
    (Test-Path -LiteralPath (Join-Path $dir 'app') -PathType Container) -and
    (Test-Path -LiteralPath (Join-Path $dir 'runtime') -PathType Container)
}

# The MSI registers itself under the per-user (or machine) Uninstall key.
$entries = @(
    Get-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*',
                     'HKLM:\Software\Microsoft\Windows\CurrentVersion\Uninstall\*' -ErrorAction SilentlyContinue |
        Where-Object { $_.DisplayName -in @($name, $legacyName) -and $_.InstallLocation }
)
$legacy = @($entries | Where-Object { $_.DisplayName -eq $legacyName }).Count -gt 0
if ($entries.Count -eq 0 -or $legacy) {
    if (-not $Installer) { throw 'Gokapi Desktop is not installed under its current name. Run the installer from `just build-exe` once first.' }
    if ($legacy) { Write-Host "Installed as $legacyName; running the installer to replace it: $Installer" }
    else { Write-Host "Not installed yet; running the installer: $Installer" }
    if ($DryRun) { Write-Host 'Dry run: nothing changed.'; return }
    $process = Start-Process -FilePath $Installer -Wait -PassThru
    if ($process.ExitCode -ne 0) { throw "Installer exited with code $($process.ExitCode)." }
    Write-Host 'Installed.'
    return
}
if ($entries.Count -gt 1) { throw "Found $($entries.Count) Gokapi Desktop installs; refusing to guess which one to replace." }
if (-not (Test-AppLayout $source)) {
    throw "No built app image at $source. Run: ./gradlew createDistributable"
}

$target = [IO.Path]::GetFullPath($entries[0].InstallLocation.TrimEnd('\'))
# The folder is user-chosen (dirChooser), so only trust it if it is not a
# drive root and already holds exactly the layout jpackage installs.
if ([IO.Path]::GetPathRoot($target).TrimEnd('\') -eq $target) { throw "Install location is a drive root: $target" }
if (-not (Test-AppLayout $target)) { throw "Install location does not look like Gokapi Desktop: $target" }

Write-Host "Installed version : $($entries[0].DisplayVersion)"
if ($Version) { Write-Host "New version       : $Version" }
Write-Host "Install location  : $target"
Write-Host "Source            : $source"
Write-Host "Replacing         : $($items -join ', ')"

# Only Gokapi Desktop processes running from the install folder lock its files.
$prefix = $target + '\'
function Get-RunningApp {
    @(Get-Process -Name $name -ErrorAction SilentlyContinue |
        Where-Object { $_.Path -and $_.Path.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase) })
}

$wasRunning = @(Get-RunningApp).Count -gt 0
if ($DryRun) {
    if ($wasRunning) { Write-Host 'Gokapi Desktop is running; the update would ask you to close it.' }
    Write-Host 'Dry run: nothing changed.'
    return
}

# Ask instead of killing the app, so a running upload or download is not cut off.
while (@(Get-RunningApp).Count -gt 0) {
    Write-Host 'Gokapi Desktop is running. Close it to install the update.'
    $answer = Read-Host 'Press Enter once it is closed, or type q to cancel'
    if ($answer -match '^\s*q') { throw 'Update cancelled; the app is still running.' }
}

# Rename in one step: Move-Item falls back to moving a folder file by file and
# can stop halfway on a locked jar, while a .NET rename either fully happens
# or fails without changing anything.
function Rename-Atomic([string]$from, [string]$to) {
    if (Test-Path -LiteralPath $from -PathType Container) { [IO.Directory]::Move($from, $to) }
    else { [IO.File]::Move($from, $to) }
}

# A closed app can keep its files open for a moment while it exits, so
# retry the first rename until the handles are released.
function Rename-WithRetry([string]$from, [string]$to) {
    $deadline = (Get-Date).AddSeconds(15)
    while ($true) {
        try { Rename-Atomic $from $to; return }
        catch {
            if ((Get-Date) -gt $deadline) { throw }
            Start-Sleep -Milliseconds 300
        }
    }
}

# Move the old items aside first so a failed copy can be rolled back.
$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$backups = @{}
try {
    foreach ($item in $items) {
        $path = Join-Path $target $item
        $backup = "$path.old-$stamp"
        Rename-WithRetry $path $backup
        $backups[$item] = $backup
    }
    foreach ($item in $items) {
        Copy-Item -LiteralPath (Join-Path $source $item) -Destination (Join-Path $target $item) -Recurse
    }
} catch {
    Write-Host "Update failed, restoring the previous files: $_"
    foreach ($item in $backups.Keys) {
        $path = Join-Path $target $item
        if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Recurse -Force }
        Rename-Atomic $backups[$item] $path
    }
    if ($wasRunning) { Start-Process -FilePath (Join-Path $target "$name.exe") }
    throw
}
foreach ($backup in $backups.Values) { Remove-Item -LiteralPath $backup -Recurse -Force }

# The swap skips the installer, so record the version it would have written.
# A per-machine (HKLM) entry needs admin rights; leave it as it is then.
if ($Version) {
    try { Set-ItemProperty -LiteralPath $entries[0].PSPath -Name DisplayVersion -Value $Version }
    catch { Write-Host "Could not record version $Version in the uninstall entry: $_" }
}

Write-Host 'Updated.'
if ($wasRunning) {
    Start-Process -FilePath (Join-Path $target "$name.exe")
    Write-Host 'Restarted the app.'
}
