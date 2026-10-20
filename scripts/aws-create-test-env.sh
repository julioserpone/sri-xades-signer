#!/usr/bin/env bash
#
# Crea o actualiza el entorno de pruebas en AWS:
#   - secreto en Secrets Manager con el PKCS#12
#   - stack SAM con Lambda, API Gateway IAM, rol y log group
#
# Uso:
#   ./scripts/aws-create-test-env.sh
#   ./scripts/aws-create-test-env.sh --p12 /ruta/certificado.p12 --alias mi-alias
#   STAGE=cert SECRET_NAME=sri-xades-signer/cert ./scripts/aws-create-test-env.sh --p12 ...
#
# Por defecto usa el perfil sri-xades, la región us-east-2 y Stage=dev. Si no se
# indica --p12 y Stage=dev, genera un certificado autofirmado solo para pruebas.
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

AWS_PROFILE="${AWS_PROFILE:-sri-xades}"
AWS_REGION="${AWS_REGION:-us-east-2}"
AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-${AWS_REGION}}"
STAGE="${STAGE:-dev}"
STACK_NAME="${STACK_NAME:-sri-xades-signer-${STAGE}}"
if [ "${STAGE}" = "dev" ]; then
  DEFAULT_SECRET_NAME="sri-xades-signer/test"
else
  DEFAULT_SECRET_NAME="sri-xades-signer/${STAGE}"
fi
SECRET_NAME="${SECRET_NAME:-${DEFAULT_SECRET_NAME}}"
RESERVED_CONCURRENCY="${RESERVED_CONCURRENCY:-0}"
RELEASE_VERSION="${RELEASE_VERSION:-v0.1.3}"
STATE_FILE="${STATE_FILE:-target/aws-test-env}"
P12_FILE=""
ALIAS=""
REBUILD=false
ZIP_OPT=""

usage() {
  sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//'
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --p12)
      [ "$#" -ge 2 ] || { echo "Falta el valor de --p12" >&2; exit 2; }
      P12_FILE="$2"
      shift 2
      ;;
    --alias)
      [ "$#" -ge 2 ] || { echo "Falta el valor de --alias" >&2; exit 2; }
      ALIAS="$2"
      shift 2
      ;;
    --rebuild)
      REBUILD=true
      shift
      ;;
    --zip)
      [ "$#" -ge 2 ] || { echo "Falta el valor de --zip" >&2; exit 2; }
      ZIP_OPT="$2"
      shift 2
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

export AWS_PROFILE AWS_REGION AWS_DEFAULT_REGION

require() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Error: falta el comando '$1'." >&2
    exit 1
  fi
}

require aws
require sam
require python3

if ! aws sts get-caller-identity >/dev/null 2>&1; then
  echo "Error: el perfil '${AWS_PROFILE}' no tiene credenciales válidas." >&2
  echo "Configúralo con: aws configure --profile ${AWS_PROFILE}" >&2
  exit 1
fi

echo "==> Cuenta AWS"
aws sts get-caller-identity --query 'Arn' --output text
echo "    Perfil: ${AWS_PROFILE}"
echo "    Región: ${AWS_REGION}"
echo "    Stack:  ${STACK_NAME}"
echo "    Stage:  ${STAGE}"

if [ -z "${P12_FILE}" ]; then
  if [ "${STAGE}" != "dev" ]; then
    echo "Error: para Stage=${STAGE} debes indicar --p12 con el certificado real." >&2
    exit 1
  fi
  require openssl
  echo "==> Generando certificado autofirmado de prueba (solo para Stage=dev)"
  mkdir -p target
  P12_FILE="target/test-cert.p12"
  P12_PASSWORD="$(openssl rand -base64 24 | tr -d '\n')"
  printf '%s\n' "${P12_PASSWORD}" > target/test-cert-password.txt
  chmod 600 target/test-cert-password.txt
  openssl req -x509 -newkey rsa:2048 \
    -keyout target/test-cert-key.pem \
    -out target/test-cert.pem \
    -days 30 -nodes \
    -subj "/CN=SRI Test $(date +%s)" >/dev/null 2>&1
  ALIAS="${ALIAS:-sri-test}"
  openssl pkcs12 -export \
    -out "${P12_FILE}" \
    -inkey target/test-cert-key.pem \
    -in target/test-cert.pem \
    -name "${ALIAS}" \
    -passout "pass:${P12_PASSWORD}" >/dev/null 2>&1
  GENERATED_TEST_CERT=true
  echo "    Certificado: ${P12_FILE}"
  echo "    Contraseña:  target/test-cert-password.txt (no lo subas a Git)"
else
  [ -f "${P12_FILE}" ] || { echo "Error: no existe ${P12_FILE}" >&2; exit 1; }
  GENERATED_TEST_CERT=false
fi

SECRET_EXISTS=false
if aws secretsmanager describe-secret \
  --secret-id "${SECRET_NAME}" \
  --region "${AWS_REGION}" \
  --profile "${AWS_PROFILE}" >/dev/null 2>&1; then
  SECRET_EXISTS=true
fi

SECRET_ARGS=("${SECRET_NAME}" "${P12_FILE}")
if [ -n "${ALIAS}" ]; then
  SECRET_ARGS+=(--alias "${ALIAS}")
fi
if [ "${SECRET_EXISTS}" = true ]; then
  SECRET_ARGS+=(--update)
  echo "==> Actualizando secreto existente: ${SECRET_NAME}"
else
  echo "==> Creando secreto: ${SECRET_NAME}"
fi

if [ "${GENERATED_TEST_CERT}" = true ]; then
  printf '%s\n' "${P12_PASSWORD}" | ./scripts/create-secret.sh "${SECRET_ARGS[@]}"
else
  ./scripts/create-secret.sh "${SECRET_ARGS[@]}"
fi

SECRET_ARN="$(aws secretsmanager describe-secret \
  --secret-id "${SECRET_NAME}" \
  --region "${AWS_REGION}" \
  --profile "${AWS_PROFILE}" \
  --query ARN \
  --output text)"

if [ -z "${SECRET_ARN}" ] || [ "${SECRET_ARN}" = "None" ]; then
  echo "Error: no se pudo obtener el ARN del secreto ${SECRET_NAME}." >&2
  exit 1
fi
echo "    ARN: ${SECRET_ARN}"

if [ "${REBUILD}" = true ]; then
  if [ -z "${JAVA_HOME:-}" ] && [ -x /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java ]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
    export PATH="${JAVA_HOME}/bin:${PATH}"
  fi
  echo "==> Compilando paquete Lambda local"
  ./scripts/package-lambda.sh
elif [ -n "${ZIP_OPT}" ]; then
  [ -f "${ZIP_OPT}" ] || { echo "Error: no existe ${ZIP_OPT}" >&2; exit 1; }
  echo "==> Usando paquete: ${ZIP_OPT}"
  cp "${ZIP_OPT}" target/lambda.zip
else
  require curl
  echo "==> Descargando lambda.zip ${RELEASE_VERSION}"
  mkdir -p target
  curl -fL --retry 3 --silent --show-error \
    "https://github.com/julioserpone/sri-xades-signer/releases/download/${RELEASE_VERSION}/lambda.zip" \
    -o target/lambda.zip
fi

if ! unzip -l target/lambda.zip | grep -Eq 'lib/.*\.jar'; then
  echo "Error: target/lambda.zip no contiene un JAR en lib/." >&2
  echo "Usa el release v0.1.3 o regenera con --rebuild." >&2
  exit 1
fi

echo "==> Desplegando stack ${STACK_NAME}"
sam deploy \
  --stack-name "${STACK_NAME}" \
  --template-file infra/template.yaml \
  --capabilities CAPABILITY_IAM \
  --resolve-s3 \
  --region "${AWS_REGION}" \
  --profile "${AWS_PROFILE}" \
  --parameter-overrides \
    Stage="${STAGE}" \
    CertificateSecretArn="${SECRET_ARN}" \
    ReservedConcurrency="${RESERVED_CONCURRENCY}" \
  --no-confirm-changeset

SIGNER_URL="$(aws cloudformation describe-stacks \
  --stack-name "${STACK_NAME}" \
  --region "${AWS_REGION}" \
  --profile "${AWS_PROFILE}" \
  --query "Stacks[0].Outputs[?OutputKey=='SignEndpoint'].OutputValue" \
  --output text)"

if [ -z "${SIGNER_URL}" ] || [ "${SIGNER_URL}" = "None" ]; then
  echo "Error: el stack no devolvió SignEndpoint." >&2
  exit 1
fi

mkdir -p "$(dirname "${STATE_FILE}")"
{
  printf 'STACK_NAME=%q\n' "${STACK_NAME}"
  printf 'AWS_PROFILE=%q\n' "${AWS_PROFILE}"
  printf 'AWS_REGION=%q\n' "${AWS_REGION}"
  printf 'SECRET_NAME=%q\n' "${SECRET_NAME}"
  printf 'SECRET_ARN=%q\n' "${SECRET_ARN}"
  printf 'STAGE=%q\n' "${STAGE}"
  printf 'SIGNER_URL=%q\n' "${SIGNER_URL}"
} > "${STATE_FILE}"
chmod 600 "${STATE_FILE}"

echo
echo "==> Entorno listo"
echo "    Endpoint: ${SIGNER_URL}"
echo "    Estado local: ${STATE_FILE}"
echo
echo "Para eliminar los recursos:"
echo "  ./scripts/aws-delete-test-env.sh"
