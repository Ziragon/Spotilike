# Путь относительно корня проекта (вызов через .bat)
$CertDir = "./docker/certs"

if (!(Get-Command mkcert -ErrorAction SilentlyContinue)) {
    Write-Host "Error: mkcert not found!" -ForegroundColor Red
    exit
}

if (!(Test-Path $CertDir)) {
    New-Item -ItemType Directory -Force -Path $CertDir | Out-Null
}

Write-Host "Checking local CA..." -ForegroundColor Gray
mkcert -install

Write-Host "Generating certificates for localhost..." -ForegroundColor Green
mkcert -cert-file "$CertDir/localhost.pem" `
       -key-file "$CertDir/localhost-key.pem" `
       localhost 127.0.0.1 ::1

Write-Host "Done!" -ForegroundColor Green