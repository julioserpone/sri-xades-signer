# Reglas de GitHub

## Protección de `main`

El repositorio usa el ruleset `.github/rulesets/main.json`, aplicado por
`scripts/setup-github.sh`.

Reglas:

- No se permite borrar `main`.
- No se permite `force push` (non-fast-forward).
- Historial lineal obligatorio.
- Pull request obligatorio.
- Se requiere el check `ci-success` y la rama debe estar actualizada (`strict`).
- 0 aprobaciones obligatorias para permitir trabajo individual, pero el CODEOWNER revisa igual.
- Se resuelven conversaciones antes de fusionar.
- Squash merge; merge commits y rebase merge deshabilitados.
- Borrado de rama después del merge.

No agregues bypass al ruleset. Si necesitas una excepción, discútela en un issue y documenta la
decisión.

## Check obligatorio

El workflow `.github/workflows/ci.yml` ejecuta:

1. `mvn -B -ntp verify` (compilación, 20+ pruebas, Spotless y SpotBugs).
2. `./scripts/package-lambda.sh --skip-build`.
3. Publica `target/lambda.zip` como artefacto.

El job agregador `ci-success` es el contexto requerido. Si falla cualquier job, `ci-success`
falla.

## Flujo de trabajo

```text
feature branch -> pull request -> CI verde -> revisión -> squash merge -> main
```

- No hagas push directo a `main`.
- Mantén la rama actualizada con `main` antes de fusionar.
- Un PR debe tener un propósito y pruebas.
- Los cambios de firma requieren ADR y validación SRI si se pretende producción.

## Configuración inicial

Con `gh` autenticado y permisos de administración:

```bash
gh repo create julioserpone/sri-xades-signer --public --source=. --remote=origin
git push -u origin main
./scripts/setup-github.sh julioserpone/sri-xades-signer
```

Verifica:

```bash
gh ruleset list --repo julioserpone/sri-xades-signer
gh api repos/julioserpone/sri-xades-signer/rulesets
```

## Releases

- Tags `vX.Y.Z` desde `main`.
- `CHANGELOG.md` actualizado.
- `mvn verify` en verde.
- El workflow de release adjunta `target/lambda.zip`.
- No publiques una release como “certificada por el SRI” sin evidencia.

## Seguridad del repositorio

- Alertas de vulnerabilidades y correcciones automáticas habilitadas por el script.
- Dependabot para Maven y GitHub Actions.
- CodeQL para Java.
- Secret scanning y push protection si el plan lo permite.
- No se aceptan credenciales, certificados ni XML reales.
