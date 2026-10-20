#!/usr/bin/env bash
#
# Configura el repositorio de GitHub con reglas de protección y seguridad.
# Requiere gh autenticado con permisos de administración.
#
# Uso:
#   ./scripts/setup-github.sh [owner/repo]
#
set -euo pipefail

REPO="${1:-julioserpone/sri-xades-signer}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if ! command -v gh >/dev/null 2>&1; then
  echo "Error: GitHub CLI (gh) no está instalado." >&2
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "Error: gh no está autenticado. Ejecute: gh auth login" >&2
  exit 1
fi

echo "==> Repositorio: ${REPO}"

gh api -X PATCH "repos/${REPO}" \
  -f description="Firmador XAdES-BES para comprobantes electrónicos del SRI de Ecuador, empaquetable como AWS Lambda." \
  -F has_issues=true \
  -F has_wiki=false \
  -F has_projects=false \
  -F allow_squash_merge=true \
  -F allow_merge_commit=false \
  -F allow_rebase_merge=false \
  -F delete_branch_on_merge=true \
  -F allow_auto_merge=true >/dev/null

gh repo edit "${REPO}" \
  --add-topic sri \
  --add-topic ecuador \
  --add-topic facturacion-electronica \
  --add-topic comprobantes-electronicos \
  --add-topic xades \
  --add-topic java \
  --add-topic aws-lambda \
  --add-topic digital-signature >/dev/null

echo "==> Habilitando alertas de seguridad"
gh api -X PUT "repos/${REPO}/vulnerability-alerts" >/dev/null || \
  echo "   Aviso: no se pudieron habilitar las alertas de vulnerabilidades."
gh api -X PUT "repos/${REPO}/automated-security-fixes" >/dev/null || \
  echo "   Aviso: no se pudieron habilitar las correcciones automáticas."
gh api -X PUT "repos/${REPO}/private-vulnerability-reporting" >/dev/null || \
  echo "   Aviso: no se pudo habilitar el reporte privado de vulnerabilidades."

EXISTING_ID="$(gh api "repos/${REPO}/rulesets" --jq '.[] | select(.name == "main-protection") | .id' 2>/dev/null | head -n 1 || true)"

if [ -n "${EXISTING_ID}" ]; then
  echo "==> Actualizando ruleset 'main-protection' (${EXISTING_ID})"
  if gh api -X PUT "repos/${REPO}/rulesets/${EXISTING_ID}" \
    --input "${ROOT}/.github/rulesets/main.json" >/dev/null; then
    echo "   Ruleset actualizado."
  else
    echo "   Aviso: no se pudo actualizar el ruleset." >&2
  fi
else
  echo "==> Creando ruleset 'main-protection'"
  if gh api -X POST "repos/${REPO}/rulesets" \
    --input "${ROOT}/.github/rulesets/main.json" >/dev/null; then
    echo "   Ruleset creado."
  else
    echo "   Aviso: no se pudo crear el ruleset." >&2
    echo "   En repos privados, algunas reglas requieren un plan compatible." >&2
  fi
fi

echo
echo "Configuración aplicada. Verifique:"
echo "  gh ruleset list --repo ${REPO}"
echo "  gh api repos/${REPO}/rulesets"
echo "  gh api repos/${REPO}/branches/main/protection"
