#Requires -Version 5.1
param(
    [ValidateSet('debug','release')]
    [string]$BuildType = '',
    [ValidateSet('femboygram','exteragram')]
    [string]$Edition = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = (Resolve-Path (Join-Path $ScriptDir '..')).Path
$LocalPropertiesFile = Join-Path $RepoRoot 'local.properties'
$GradlewBat = Join-Path $RepoRoot 'gradlew.bat'

$TestApiId = '17349'
$TestApiHash = '344583e45741c457fe1862106095a5eb'

function Get-ApiCredentials {
    if ($env:FEMBOYGRAM_API_ID -match '^\d+$' -and $env:FEMBOYGRAM_API_HASH -match '^[0-9a-fA-F]{32}$') {
        return @{
            Id = $env:FEMBOYGRAM_API_ID.Trim()
            Hash = $env:FEMBOYGRAM_API_HASH.Trim()
            Source = 'environment'
        }
    }

    $candidates = @(
        (Join-Path $RepoRoot 'api_credentials.local.ps1'),
        (Join-Path (Split-Path $RepoRoot -Parent) 'api_credentials.local.ps1')
    )
    foreach ($file in $candidates) {
        if (-not (Test-Path $file)) { continue }
        . $file
        $id = [string]$TDESKTOP_API_ID
        $hash = [string]$TDESKTOP_API_HASH
        if ($id.Trim() -match '^\d+$' -and $hash.Trim() -match '^[0-9a-fA-F]{32}$') {
            return @{
                Id = $id.Trim()
                Hash = $hash.Trim()
                Source = (Split-Path $file -Leaf)
            }
        }
        Write-Host "[WARN] $file still contains placeholder or invalid credentials." -ForegroundColor Yellow
    }

    throw "FemboyGram API credentials are missing. Set FEMBOYGRAM_API_ID/FEMBOYGRAM_API_HASH or fill api_credentials.local.ps1 from https://my.telegram.org/apps."
}

function Write-Step([string]$Text) { Write-Host "`n>> $Text" -ForegroundColor Yellow }
function Write-Ok([string]$Text)   { Write-Host "[OK] $Text" -ForegroundColor Green }
function Write-Err([string]$Text)   { Write-Host "[X] $Text" -ForegroundColor Red }

function Read-BuildType {
    Write-Host ''
    Write-Host '  Build type:' -ForegroundColor Cyan
    Write-Host '    [1] debug   - fast, unsigned, for testing'
    Write-Host '    [2] release - optimized, requires keystore'
    Write-Host ''
    do {
        $choice = (Read-Host '  Choice [1/2]').Trim()
    } while ($choice -ne '1' -and $choice -ne '2')
    if ($choice -eq '2') { return 'release' }
    return 'debug'
}

function Read-Edition {
    Write-Host ''
    Write-Host '  App edition:' -ForegroundColor Cyan
    Write-Host '    [1] FemboyGram'
    Write-Host '    [2] ExteraGram'
    Write-Host ''
    do {
        $choice = (Read-Host '  Choice [1/2]').Trim()
    } while ($choice -ne '1' -and $choice -ne '2')
    if ($choice -eq '2') { return 'exteragram' }
    return 'femboygram'
}

function Set-ApiCredentials([string]$ApiId, [string]$ApiHash) {
    if ($ApiId -notmatch '^\d+$') {
        throw 'FEMBOYGRAM_API_ID must contain digits only.'
    }
    if ($ApiHash -notmatch '^[0-9a-fA-F]{32}$') {
        throw 'FEMBOYGRAM_API_HASH must be a 32-character hexadecimal value.'
    }
    $env:FEMBOYGRAM_API_ID = $ApiId
    $env:FEMBOYGRAM_API_HASH = $ApiHash
    Write-Ok "API credentials passed to Gradle (id $ApiId)"
}

function Get-JavaMajorVersion {
    param([string]$JavaExe)
    if ([string]::IsNullOrWhiteSpace($JavaExe)) { return $null }
    if (-not (Test-Path -LiteralPath $JavaExe)) { return $null }
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = (& $JavaExe -version 2>&1 | ForEach-Object { "$_" }) -join "`n"
    }
    finally {
        $ErrorActionPreference = $prev
    }
    # Java 8 prints "1.8.0_…"; Java 9+ prints "17.0.…" — match the 1.x form first.
    if ($output -match 'version "1\.(\d+)') { return [int]$Matches[1] }
    if ($output -match 'version "(\d+)') { return [int]$Matches[1] }
    return $null
}

function Find-Jdk17Home {
    $candidates = [System.Collections.Generic.List[string]]::new()
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $candidates.Add($env:JAVA_HOME)
    }
    foreach ($path in @(
        'C:\Program Files\Eclipse Adoptium\jdk-17.0.6.10-hotspot',
        'C:\Program Files\Android\Android Studio\jbr'
    )) {
        $candidates.Add($path)
    }
    if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
        $candidates.Add((Join-Path $env:LOCALAPPDATA 'Programs\Android\Android Studio\jbr'))
    }
    foreach ($glob in @(
        'C:\Program Files\Eclipse Adoptium\jdk-17*',
        'C:\Program Files\Java\jdk-17*',
        'C:\Program Files\Microsoft\jdk-17*'
    )) {
        foreach ($hit in @(Get-Item -Path $glob -ErrorAction SilentlyContinue)) {
            if ($hit -and $hit.FullName) { $candidates.Add($hit.FullName) }
        }
    }

    foreach ($jdkDir in $candidates) {
        if ([string]::IsNullOrWhiteSpace($jdkDir)) { continue }
        $javaExe = Join-Path $jdkDir 'bin\java.exe'
        $major = Get-JavaMajorVersion -JavaExe $javaExe
        if ($major -ge 17) { return $jdkDir }
    }
    return $null
}

function Ensure-Java17 {
    $currentMajor = $null
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $currentMajor = Get-JavaMajorVersion -JavaExe (Join-Path $env:JAVA_HOME 'bin\java.exe')
    }
    if (-not $currentMajor) {
        $javaCmd = Get-Command java -ErrorAction SilentlyContinue
        if ($javaCmd -and $javaCmd.Source) {
            $currentMajor = Get-JavaMajorVersion -JavaExe $javaCmd.Source
        }
    }
    if ($currentMajor -ge 17) {
        if ($env:JAVA_HOME) {
            Write-Ok "JAVA_HOME: $env:JAVA_HOME (JDK $currentMajor)"
        }
        else {
            Write-Ok "java: JDK $currentMajor"
        }
        return
    }

    $jdkHome = Find-Jdk17Home
    if ([string]::IsNullOrWhiteSpace($jdkHome)) {
        throw "JDK 17+ required (found: $(if ($currentMajor) { $currentMajor } else { 'none' })). Install Temurin/Oracle JDK 17 and set JAVA_HOME."
    }

    $env:JAVA_HOME = $jdkHome
    $env:Path = "$(Join-Path $jdkHome 'bin');$env:Path"
    $switched = Get-JavaMajorVersion -JavaExe (Join-Path $jdkHome 'bin\java.exe')
    Write-Ok "JAVA_HOME switched to JDK $switched : $jdkHome"
}

function Find-AndroidSdk {
    foreach ($path in @($env:OWPNG_ANDROID_SDK, $env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if ($path -and (Test-Path $path)) { return (Resolve-Path $path).Path }
    }
    $default = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (Test-Path $default) { return (Resolve-Path $default).Path }
    return $null
}

function Ensure-LocalProperties([string]$SdkPath) {
    $escaped = $SdkPath -replace '\\', '\\'
    $line = "sdk.dir=$escaped"
    if (Test-Path $LocalPropertiesFile) {
        $content = Get-Content -Path $LocalPropertiesFile -Raw -Encoding UTF8
        if ($content -match '(?m)^sdk\.dir=.*$') {
            $updated = [regex]::Replace($content, '(?m)^sdk\.dir=.*$', $line)
            [System.IO.File]::WriteAllText($LocalPropertiesFile, $updated, (New-Object System.Text.UTF8Encoding $false))
            return
        }
    }
    [System.IO.File]::WriteAllText($LocalPropertiesFile, "$line`r`n", (New-Object System.Text.UTF8Encoding $false))
}

function Invoke-Gradle([string]$Task, [string]$Label) {
    $logDir = Join-Path $RepoRoot 'logs'
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    $logFile = Join-Path $logDir ("{0}-{1}.log" -f $Label, (Get-Date -Format 'yyyyMMdd-HHmmss'))

    $escapedWd = $RepoRoot.Replace('"', '""')
    $escapedLog = $logFile.Replace('"', '""')
    $escapedGradlew = $GradlewBat.Replace('"', '""')
    $full = "cd /d `"$escapedWd`" && call `"$escapedGradlew`" $Task >> `"$escapedLog`" 2>&1"

    Write-Host "gradle: $Task" -ForegroundColor DarkGray
    Write-Host "log: $logFile" -ForegroundColor DarkGray

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = 'cmd.exe'
    $psi.Arguments = "/c `"$full`""
    $psi.WorkingDirectory = $RepoRoot
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $psi
    $null = $process.Start()

    $offset = 0L
    $lastBeat = [datetime]::UtcNow
    while (-not $process.HasExited) {
        if (Test-Path $logFile) {
            $stream = [System.IO.File]::Open($logFile, 'Open', 'Read', 'ReadWrite')
            try {
                $stream.Seek($offset, 'Begin') | Out-Null
                $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)
                while (-not $reader.EndOfStream) {
                    $line = $reader.ReadLine()
                    if (-not [string]::IsNullOrWhiteSpace($line)) { Write-Host $line }
                }
                $offset = $stream.Position
            }
            finally { $stream.Dispose() }
        }
        if (([datetime]::UtcNow - $lastBeat).TotalSeconds -ge 30) {
            $lastBeat = [datetime]::UtcNow
            Write-Host "[$(Get-Date -Format 'HH:mm:ss')] $Label - still running..." -ForegroundColor Cyan
        }
        Start-Sleep -Milliseconds 500
    }

    if ($process.ExitCode -ne 0) {
        throw "Gradle failed (exit $($process.ExitCode)). Log: $logFile"
    }
    Write-Ok "Finished: $Label"
}

try {
    Write-Host ''
    Write-Host 'OwpenGram Android build' -ForegroundColor Cyan
    Write-Host "Repo: $RepoRoot"
    Write-Host 'Tip: run build-android.bat from cmd or PowerShell (not Git Bash).' -ForegroundColor DarkGray

    # --- Edition and build type ---
    if ([string]::IsNullOrWhiteSpace($Edition)) {
        $Edition = Read-Edition
    }
    if ([string]::IsNullOrWhiteSpace($BuildType)) {
        $BuildType = Read-BuildType
    }

    # The edition dimension gives each app its own package id, while the
    # standalone build type remains the unrestricted direct-download variant.
    $GradleModule  = ':TMessagesProj_AppStandalone'
    $ApkBuildTypeDir = if ($BuildType -eq 'release') { 'standalone' } else { 'debug' }
    $BuildTypeCap  = (Get-Culture).TextInfo.ToTitleCase($ApkBuildTypeDir)
    $EditionCap = (Get-Culture).TextInfo.ToTitleCase($Edition)
    $GradleTask = "${GradleModule}:assemble${EditionCap}Afat$BuildTypeCap"
    $ApkPath = Join-Path $RepoRoot "TMessagesProj_AppStandalone\build\outputs\apk\$Edition\afat\$ApkBuildTypeDir\app.apk"

    Write-Ok "Edition: $Edition"
    Write-Ok "Build type: $BuildType"

    # OWPENGRAM_BUILD is the release number this build ships under -- the N in
    # GitHub tag "ON". The in-app updater offers any published release with a
    # higher number and shows it as "ON". Same rule as the desktop client: an
    # OWPENGRAM_BUILD environment variable skips the prompt (scripted use), and
    # 0 means a local dev build that never checks for updates.
    if ($env:OWPENGRAM_BUILD) {
        $OwpengramBuild = $env:OWPENGRAM_BUILD
        if ($OwpengramBuild -notmatch '^\d+$') { throw "OWPENGRAM_BUILD must be a whole number, got '$OwpengramBuild'" }
    } else {
        $OwpengramBuild = $null
        do {
            $answer = (Read-Host '  Release number for this build (the N in tag ON; blank = local dev build, no self-update)').Trim()
            if ([string]::IsNullOrEmpty($answer)) { $answer = '0' }
            if ($answer -match '^\d+$') {
                $OwpengramBuild = $answer
            } else {
                Write-Host '  Enter a whole number, or leave blank for a local dev build.' -ForegroundColor Yellow
            }
        } while ($null -eq $OwpengramBuild)
    }
    if ($OwpengramBuild -eq '0') {
        Write-Ok 'Release number: 0 (local dev build -- self-update stays off)'
    } else {
        Write-Ok "Release number: $OwpengramBuild (must match tag O$OwpengramBuild once this is published)"
    }
    # A -P project property beats gradle.properties, so nothing in the repo is edited
    $GradleTask = "$GradleTask -POWPENGRAM_BUILD=$OwpengramBuild"

    # --- API credentials ---
    $api = Get-ApiCredentials
    Write-Ok "API credentials: $($api.Source) (id $($api.Id))"
    Set-ApiCredentials -ApiId $api.Id -ApiHash $api.Hash

    # --- Tools check ---
    Write-Step 'Check tools'
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'Missing git in PATH' }
    Ensure-Java17
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Missing java in PATH (JDK 17+)' }
    if (-not (Test-Path $GradlewBat)) { throw "Missing: $GradlewBat" }

    $sdk = Find-AndroidSdk
    if (-not $sdk) {
        throw 'Android SDK not found. Install Android Studio or set ANDROID_HOME.'
    }
    Ensure-LocalProperties -SdkPath $sdk
    Write-Ok "SDK: $sdk"

    Write-Step 'Git submodules'
    Push-Location $RepoRoot
    try {
        $env:GIT_TERMINAL_PROMPT = '0'
        & git -c advice.detachedHead=false submodule update --init --recursive --quiet
        if ($LASTEXITCODE -ne 0) { throw "git submodule failed ($LASTEXITCODE)" }
    }
    finally { Pop-Location }
    Write-Ok 'Submodules OK'

    Write-Step "Gradle $GradleTask"
    Invoke-Gradle -Task $GradleTask -Label "assemble-$Edition-$BuildType"

    if (Test-Path $ApkPath) {
        Write-Ok "APK: $ApkPath"
    }
    else {
        Write-Host "[WARN] APK not found at: $ApkPath" -ForegroundColor Yellow
        Write-Host "       Check folder: $(Join-Path $RepoRoot 'TMessagesProj_AppStandalone\build\outputs\apk')" -ForegroundColor Yellow
    }
}
catch {
    Write-Err $_.Exception.Message
    exit 1
}
