#!/usr/bin/env bash
#
# Elimina el entorno de pruebas creado por aws-create-test-env.sh:
#   - stack SAM (Lambda, API Gateway, rol, log group)
#   - secreto de Secrets Manager
#
# Uso:
#   ./scripts/aws-delete-test-env.sh
#   ./scripts/aws-delete-test-env.sh --yes
#
# Lee target/aws-test-env si existe. También puedes sobreescribir STACK_NAME,
# SECRET_NAME, AWS_PROFILE, AWS_REGION y STATE_FILE por variables de entorno.
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

STATE_FILE="${STATE_FILE:-target/aws-test-env}"
AWS_PROFILE="${AWS_PROFILE:-sri-xades}"
AWS_REGION="${AWS_REGION:-us-east-2}"
STACK_NAME="${STACK_NAME:-sri-xades-signer-dev}"
SECRET_NAME="${SECRET_NAME:-sri-xades-signer/test}"
ASSUME_YES=false

if [ -f "${STATE_FILE}" ]; then
  # shellcheck disable=SC1090
  source "${STATE_FILE}"
fi

usage() {
  sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --yes|-y)
      ASSUME_YES=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Opción desconocida: $1" >&2
      exit 2
      ;;
  esac
done

export AWS_PROFILE AWS_REGION AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-${AWS_REGION}}"

if ! command -v aws >/dev/null 2>&1; then
  echo "Error: AWS CLI v2 no está instalado." >&2
  exit 1
fi
if ! command -v sam >/dev/null 2>&1; then
  echo "Error: AWS SAM CLI no está instalado." >&2
  exit 1
fi

if ! aws sts get-caller-identity >/dev/null 2>&1; then
  echo "Error: el perfil '${AWS_PROFILE}' no tiene credenciales válidas." >&2
  exit 1
fi

echo "Se eliminarán:"
echo "  Stack:  ${STACK_NAME} (${AWS_REGION})"
echo "  Secreto: ${SECRET_NAME} (borrado irreversible)"
echo "  Perfil: ${AWS_PROFILE}"

if [ "${ASSUME_YES}" != true ]; then
  read -r -p "Escribe ELIMINAR para continuar: " CONFIRM
  if [ "${CONFIRM}" != "ELIMINAR" ]; then
    echo "Cancelado."
    exit 0
  fi
fi

if aws cloudformation describe-stacks \
  --stack-name "${STACK_NAME}" \
  --region "${AWS_REGION}" \
  --profile "${AWS_PROFILE}" >/dev/null 2>&1; then
  echo "==> Eliminando stack ${STACK_NAME}"
  sam delete \
    --stack-name "${STACK_NAME}" \
    --region "${AWS_REGION}" \
    --profile "${AWS_PROFILE}" \
    --no-prompts
else
  echo "==> El stack ${STACK_NAME} no existe; se omite."
fi

if aws secretsmanager describe-secret \
  --secret-id "${SECRET_NAME}" \
  --region "${AWS_REGION}" \
  --profile "${AWS_PROFILE}" >/dev/null 2>&1; then
  echo "==> Eliminando secreto ${SECRET_NAME}"
  aws secretsmanager delete-secret \
    --secret-id "${SECRET_NAME}" \
    --region "${AWS_REGION}" \
    --profile "${AWS_PROFILE}" \
    --force-delete-without-recovery >/dev/null
  echo "    Secreto eliminado."
else
  echo "==> El secreto ${SECRET_NAME} no existe; se omite."
fi

rm -f "${STATE_FILE}"

echo
echo "==> Entorno eliminado."
echo "    No se eliminó el usuario IAM ${AWS_PROFILE} ni sus access keys."
echo "    Los archivos target/test-cert* se conservan por si los necesitas."
