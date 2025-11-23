# Script to check keystore information
# This helps you verify your keystore file and see available aliases

param(
    [string]$KeystorePath = "app\liftpath-release-key.jks",
    [string]$KeystorePassword
)

if (-not $KeystorePassword) {
    Write-Host "Usage: .\check-keystore.ps1 -KeystorePassword 'your_password'" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "This script will list all key aliases in your keystore." -ForegroundColor Yellow
    Write-Host "You'll need this information to create keystore.properties" -ForegroundColor Yellow
    exit
}

if (-not (Test-Path $KeystorePath)) {
    Write-Host "Error: Keystore file not found at $KeystorePath" -ForegroundColor Red
    exit
}

$javaHome = $env:JAVA_HOME
if (-not $javaHome) {
    $javaHome = "C:\Program Files\Android\Android Studio\jbr"
}

$keytoolPath = Join-Path $javaHome "bin\keytool.exe"

if (-not (Test-Path $keytoolPath)) {
    Write-Host "Error: keytool not found at $keytoolPath" -ForegroundColor Red
    Write-Host "Please set JAVA_HOME or update the script path" -ForegroundColor Yellow
    exit
}

Write-Host "Checking keystore: $KeystorePath" -ForegroundColor Green
Write-Host ""

& $keytoolPath -list -v -keystore $KeystorePath -storepass $KeystorePassword


