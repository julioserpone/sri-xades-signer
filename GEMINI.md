# GEMINI.md

Las reglas del proyecto están en [`AGENTS.md`](AGENTS.md). Léelas antes de modificar código.

Puntos críticos:

- Firma XAdES-BES 1.3.2, RSA-SHA1, SHA-1, UTF-8, `SigningCertificate` v1 y `KeyInfo` firmado.
- El Lambda no debe ser un oráculo público: API Gateway con IAM.
- La clave privada se obtiene de Secrets Manager; nunca de variables de entorno ni del repo.
- El parser XML debe seguir bloqueando DTD, entidades externas y XInclude.
- No cambies el perfil ni las dependencias de firma sin ADR, pruebas y revisión.
- El proyecto es experimental hasta la validación en certificación del SRI.

Comandos: `mvn -B verify`, `mvn -B spotless:apply`, `./scripts/package-lambda.sh`.
