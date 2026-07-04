#!/usr/bin/env bash
### DEPRECATED
# Путь относительно корня проекта
CERT_DIR="./docker/certs"

# Проверка mkcert в системе
if ! command -v mkcert &> /dev/null; then
    echo -e "\e[31mError: mkcert not found!\e[0m"
    exit 1
fi

mkdir -p "$CERT_DIR"

echo -e "\e[90mChecking local CA...\e[0m"
mkcert -install

echo -e "\e[32mGenerating certificates for localhost...\e[0m"
mkcert -cert-file "$CERT_DIR/localhost.pem" \
       -key-file "$CERT_DIR/localhost-key.pem" \
       localhost 127.0.0.1 ::1

echo -e "\e[32mDone!\e[0m"