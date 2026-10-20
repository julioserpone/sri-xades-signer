# Changelog

Todos los cambios relevantes se documentan aquí. El formato sigue
[Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y el versionado semántico.

## [Unreleased]

## [0.1.1] - 2026-10-20

### Cambiado

- BouncyCastle actualizado a 1.86, manteniendo alineados `bcprov` y `bcpkix`.
- Jackson actualizado a 2.22.3 con las correcciones de seguridad de la rama 2.x.
- AWS Lambda Java Core actualizado a 1.4.1 y SLF4J a 2.0.20.
- Plugins Maven actualizados: compiler, surefire, shade, enforcer y SpotBugs.
- Documentación de configuración inicial de AWS y despliegue sin compilar Java.
- El workflow de release publica los tags como prerelease mientras no exista certificación SRI.

### Nota

- Este release sigue siendo experimental y no está certificado por el SRI.

## [0.1.0] - 2026-10-20

### Agregado

- Firmador XAdES-BES enveloped para comprobantes electrónicos del SRI.
- Perfil XAdES 1.3.2 con `SigningCertificate` v1, RSA-SHA1, SHA-1, UTF-8 y `KeyInfo` firmado.
- Referencia explícita al `id` de la raíz con transformada `enveloped-signature`.
- `DataObjectFormat` con descripción `contenido comprobante` y MIME `text/xml`.
- Carga de PKCS#12 con validación de clave RSA y correspondencia con el certificado.
- Carga del PKCS#12 desde AWS Secrets Manager con caché y rotación por TTL.
- Handler AWS Lambda para `POST /v1/sign` con autenticación IAM.
- Parser XML endurecido contra XXE, DTD, entidades externas y XInclude.
- Límite de tamaño, lista de raíces SRI permitidas y rechazo de documentos ya firmados.
- Verificación XMLDSig posterior a la firma.
- CLI local para pruebas sin AWS.
- Empaquetado Maven Shade y `scripts/package-lambda.sh` para `target/lambda.zip`.
- Plantilla SAM con API Gateway IAM, permisos mínimos, concurrencia reservada y throttling.
- Workflows de CI y release, Dependabot, CodeQL y plantillas de issues/PR.
- Reglas para agentes de IA (`AGENTS.md`, `CLAUDE.md`, `GEMINI.md`, Copilot, Cursor y OpenCode).
- Documentación de API, despliegue, seguridad, modelo de amenazas, ADR y certificación SRI.

### Seguridad

- Prohibido el uso de certificados o contraseñas en el repositorio.
- Endpoint sin acceso público: requiere SigV4 con IAM.
- Logs sin XML, secretos ni material de clave.
- Protección de `main` con PR obligatorio, historial lineal y check `ci-success`.

### Limitaciones conocidas

- **No validado en el ambiente de certificación del SRI.**
- No incluye sellos de tiempo, OCSP ni validación de revocación en línea.
- Una firma por invocación; no hay batch.
- La imagen Docker es opcional y no se usa en el flujo principal de despliegue.

[Unreleased]: https://github.com/julioserpone/sri-xades-signer/compare/v0.1.1...HEAD
[0.1.1]: https://github.com/julioserpone/sri-xades-signer/releases/tag/v0.1.1
[0.1.0]: https://github.com/julioserpone/sri-xades-signer/releases/tag/v0.1.0
