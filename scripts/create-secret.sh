#!/usr/bin/env bash
#
# Crea o actualiza el secreto de Secrets Manager con el PKCS#12 y su contraseña.
# Uso local; no ejecutar en CI con credenciales compartidas.
#
# Uso:
#   ./scripts/create-secret.sh <secret-name> <archivo.p12> [--alias ALIAS] [--update]
#
set -euo pipefail

umask 077

if [ "$#" -lt 2 ]; then
  sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'
  exit 2
fi

SECRET_NAME="$1"
P12_FILE="$2"
shift 2

ALIAS=""
UPDATE=false
while [ "$#" -gt 0 ]; do
  case "$1" in
    --alias)
      [ "$#" -ge 2 ] || { echo "Falta el valor de --alias" >&2; exit 2; }
      ALIAS="$2"
      shift 2
      ;;
    --update)
      UPDATE=true
      shift
      ;;
    *)
      echo "Opción desconocida: $1" >&2
      exit 2
      ;;
  esac
done

if ! command -v aws >/dev/null 2>&1; then
  echo "Error: AWS CLI v2 no está instalado." >&2
  exit 1
fi
if ! command -v python3 >/dev/null 2>&1; then
  echo "Error: Python 3 no está instalado." >&2
  exit 1
fi
if [ ! -f "${P12_FILE}" ]; then
  echo "Error: no existe el archivo ${P12_FILE}" >&2
  exit 1
fi

read -r -s -p "Password del PKCS#12: " P12_PASSWORD
echo
if [ -z "${P12_PASSWORD}" ]; then
  echo "Error: la contraseña no puede estar vacía." >&2
  exit 1
fi

P12_B64="$(base64 < "${P12_FILE}" | tr -d '\n')"
TMP_JSON="$(mktemp "${TMPDIR:-/tmp}/sri-secret.XXXXXX.json")"
cleanup() {
  rm -f "${TMP_JSON}"
  unset P12_PASSWORD P12_B64
}
trap cleanup EXIT

P12_B64="${P12_B64}" P12_PASSWORD="${P12_PASSWORD}" ALIAS="${ALIAS}" python3 - "${TMP_JSON}" <<'PY'
import json
import os
import sys

payload = {
    "p12Base64": os.environ["P12_B64"],
    "password": os.environ["P12_PASSWORD"],
}
alias = os.environ.get("ALIAS", "")
if alias:
    payload["alias"] = alias

with open(sys.argv[1], "w", encoding="utf-8") as handle:
    json.dump(payload, handle, separators=(",", ":"))
PY

echo "==> Secreto: ${SECRET_NAME}"
if [ "${UPDATE}" = true ]; then
  aws secretsmanager put-secret-value \
    --secret-id "${SECRET_NAME}" \
    --secret-string "file://${TMP_JSON}" >/dev/null
  echo "   Secreto actualizado."
else
  aws secretsmanager create-secret \
    --name "${SECRET_NAME}" \
    --secret-string "file://${TMP_JSON}" \
    --query 'ARN' \
    --output text
fi

echo "   Recuerda otorgar secretsmanager:GetSecretValue al rol de la Lambda."
