#!/usr/bin/env bash
### DEPRECATED
cd "$(dirname "$0")" || exit

echo "[1/1] Generating SSL certificates for localhost..."
bash "scripts/generate-certs.sh"

echo
echo "=========================================="
echo "Project initialization complete!"
echo "=========================================="

read -n 1 -s -r -p "Press any key to continue . . ."
echo