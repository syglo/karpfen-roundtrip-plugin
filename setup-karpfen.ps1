$ErrorActionPreference = "Stop"

$rootDir = $PSScriptRoot
$propsFile = Join-Path $rootDir "gradle.properties"
$libsDir = Join-Path $rootDir "libs"
$targetJar = Join-Path $libsDir "karpfen-dsl-tools.jar"
$tempBuildDir = Join-Path $rootDir "temp_karpfen_build"
$repoUrl = "https://github.com/syglo/karpfen-dsl-tools.git"
$commitHash = "859a95d"

if (Test-Path $propsFile) {
    Get-Content $propsFile | ForEach-Object {
        if ($_ -match "^\s*org\.gradle\.java\.home\s*=\s*(.+)$") {
            $configuredJvm = $matches[1].Trim().Replace('/', '\')
            if (Test-Path $configuredJvm) {
                $env:JAVA_HOME = $configuredJvm
                $env:PATH = "$configuredJvm\bin;$env:PATH"
                Write-Host "==> Using Java runtime from gradle.properties: $configuredJvm"
            }
        }
    }
}

if (-not (Test-Path $libsDir)) {
    Write-Host "==> Creating libs directory if not present..."
    New-Item -ItemType Directory -Path $libsDir -Force | Out-Null
}

if (Test-Path $tempBuildDir) {
    Get-ChildItem -Path $tempBuildDir -Recurse -Force -ErrorAction SilentlyContinue | ForEach-Object { $_.Attributes = 'Normal' }
    Remove-Item -Path $tempBuildDir -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host "==> Cloning karpfen-dsl-tools repository..."
git clone $repoUrl $tempBuildDir

Set-Location $tempBuildDir

Write-Host "==> Checking out commit hash: $commitHash..."
git checkout $commitHash

Write-Host "==> Building JAR using Gradle wrapper..."
if ($IsWindows -or ($env:OS -like "*Windows*")) {
    & .\gradlew.bat jar --no-daemon --warning-mode none
} else {
    chmod +x ./gradlew
    & ./gradlew jar --no-daemon --warning-mode none
}

$buildLibsDir = Join-Path $tempBuildDir "build\libs"
if (-not (Test-Path $buildLibsDir)) {
    Set-Location $rootDir
    throw "Build failed: output directory '$buildLibsDir' does not exist."
}

$builtJar = Get-ChildItem -Path $buildLibsDir -Filter "*.jar" | Select-Object -First 1
if (-not $builtJar) {
    Set-Location $rootDir
    throw "No JAR artifact found in $buildLibsDir"
}

Write-Host "==> Copying $($builtJar.FullName) to $targetJar..."
Copy-Item -Path $builtJar.FullName -Destination $targetJar -Force
Write-Host "==> SUCCESS! karpfen-dsl-tools.jar copied to libs/"

Set-Location $rootDir

Write-Host "==> Cleaning up temporary files..."
if (Test-Path $tempBuildDir) {
    Get-ChildItem -Path $tempBuildDir -Recurse -Force -ErrorAction SilentlyContinue | ForEach-Object { $_.Attributes = 'Normal' }
    Remove-Item -Path $tempBuildDir -Recurse -Force -ErrorAction SilentlyContinue
}