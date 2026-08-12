# Stop on any error
$ErrorActionPreference = "Stop"

$repoUrl = "https://github.com/karpfenproject/karpfen-dsl-tools.git"
$commitHash = "859a95d"
$tempDir = "temp_karpfen_build"
$targetLibsDir = "libs"
$targetJarName = "karpfen-dsl-tools.jar"

Write-Host "==> Creating libs directory if not present..." -ForegroundColor Cyan
if (-not (Test-Path $targetLibsDir)) {
    New-Item -ItemType Directory -Path $targetLibsDir | Out-Null
}

# Clean up temporary clone directory if it already exists from a previous run
if (Test-Path $tempDir) {
    Write-Host "==> Cleaning up previous build folder..." -ForegroundColor Yellow
    Remove-Item -Path $tempDir -Recurse -Force
}

Write-Host "==> Cloning karpfen-dsl-tools repository..." -ForegroundColor Cyan
git clone $repoUrl $tempDir

# Change directory into the cloned repo
Push-Location $tempDir

try {
    Write-Host "==> Checking out commit hash: $commitHash..." -ForegroundColor Cyan
    git checkout $commitHash

    Write-Host "==> Building JAR using Gradle wrapper..." -ForegroundColor Cyan
    if ($IsWindows -or ($env:OS -like "*Windows*")) {
        .\gradlew.bat jar
    } else {
        chmod +x ./gradlew
        ./gradlew jar
    }

    # Find the generated jar inside build/libs/
    $builtJar = Get-ChildItem -Path "build/libs" -Filter "*.jar" | Select-Object -First 1

    if (-not $builtJar) {
        throw "Build failed: No output JAR file found in build/libs/"
    }

    # Store full path before leaving the directory
    $sourceJarPath = $builtJar.FullName

    Pop-Location # Return back to parent project root

    $destinationPath = Join-Path $targetLibsDir $targetJarName
    Write-Host "==> Copying $sourceJarPath to $destinationPath..." -ForegroundColor Green
    Copy-Item -Path $sourceJarPath -Destination $destinationPath -Force

    Write-Host "==> SUCCESS! $targetJarName copied to $targetLibsDir/" -ForegroundColor Green

} finally {
    # Ensure directory context is restored if an error occurs
    if ((Get-Location).Path -like "*$tempDir*") {
        Pop-Location
    }
    # Clean up temp folder
    if (Test-Path $tempDir) {
        Write-Host "==> Cleaning up temporary files..." -ForegroundColor Gray
        Remove-Item -Path $tempDir -Recurse -Force
    }
}