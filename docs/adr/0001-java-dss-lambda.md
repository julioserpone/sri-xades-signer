# ADR 0001: Java 21 + EU DSS para firmar XAdES-BES

- Estado: Aceptado
- Fecha: 2026-10-20

## Contexto

El SRI exige XAdES-BES con RSA-SHA1, SHA-1, `SigningCertificate` v1 y `KeyInfo` firmado. Implementar
XMLDSig/XAdES a mano es propenso a errores de canonicalización, referencias y validación. El
servicio debe ejecutarse como AWS Lambda y ser fácil de empaquetar.

## Decisión

Usar **Java 21** y **EU Digital Signature Service (DSS) 6.5.1**, con BouncyCastle y empaquetado
Maven Shade. El Lambda se entrega como ZIP con un JAR shaded.

## Alternativas consideradas

- **PHP + xmlseclibs:** ya se usa en el cliente, pero el ecosistema XAdES es más limitado y el
  empaquetado Lambda requiere capas/binarios.
- **Python + signxml:** buena para XMLDSig, pero XAdES-BES y la estructura exacta requieren más
  trabajo manual.
- **Node.js + xmldsigjs:** útil, pero la validación y el perfil XAdES no están tan maduros.
- **Implementación manual:** descartada por riesgo criptográfico y de interoperabilidad.

## Consecuencias

- JAR grande (~32 MB) pero dentro de los límites de Lambda.
- Dependencia LGPL-2.1 de DSS; se usa sin modificaciones y se documenta en
  `THIRD-PARTY-NOTICES.md`.
- Java 21 debe estar disponible en CI y en el entorno local.
- El perfil se puede validar con XMLDSig y con herramientas DSS/SRI.
