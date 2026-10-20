#!/usr/bin/env bash
#
# Genera target/lambda.zip con el JAR shaded listo para AWS Lambda.
#
# Uso:
#   ./scripts/package-lambda.sh                 # ejecuta mvn verify y empaqueta
#   ./scripts/package-lambda.sh --skip-build    # usa el JAR ya verificado
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT}"

SKIP_BUILD=false
for arg in "$@"; do
  case "${arg}" in
    --skip-build) SKIP_BUILD=true ;;
    -h|--help)
      sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      echo "Opción desconocida: ${arg}" >&2
      exit 2
      ;;
  esac
done

if ! command -v zip >/dev/null 2>&1; then
  echo "Error: se requiere el comando zip." >&2
  exit 1
fi

if [ "${SKIP_BUILD}" = false ]; then
  echo "==> Ejecutando mvn -B clean verify"
  mvn -B -ntp clean verify
fi

VERSION="$(mvn -B -q help:evaluate -Dexpression=project.version -DforceStdout)"
JAR="target/sri-xades-signer-${VERSION}.jar"

if [ ! -f "${JAR}" ]; then
  echo "Error: no se encontró ${JAR}. Ejecuta mvn verify primero." >&2
  exit 1
fi

JAR_LIST="$(mktemp "${TMPDIR:-/tmp}/sri-jar-list.XXXXXX")"
cleanup() {
  rm -f "${JAR_LIST}"
}
trap cleanup EXIT

unzip -l "${JAR}" > "${JAR_LIST}"
if ! grep -q 'com/julioserpone/srixadessigner/lambda/SignHandler.class' "${JAR_LIST}"; then
  echo "Error: el JAR no contiene el handler de Lambda." >&2
  exit 1
fi

rm -f target/lambda.zip
zip -j -q target/lambda.zip "${JAR}"

SIZE="$(du -h target/lambda.zip | cut -f1)"
if command -v shasum >/dev/null 2>&1; then
  SHA="$(shasum -a 256 target/lambda.zip | awk '{print $1}')"
else
  SHA="$(sha256sum target/lambda.zip | awk '{print $1}')"
fi

echo
echo "==> Paquete: target/lambda.zip (${SIZE})"
echo "==> SHA-256: ${SHA}"
echo "==> Contenido:"
unzip -l target/lambda.zip | sed -n '1,8p'
echo
echo "Siguiente paso:"
echo "  aws lambda update-function-code --function-name sri-xades-signer --zip-file fileb://target/lambda.zip"
