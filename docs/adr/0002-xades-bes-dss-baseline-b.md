# ADR 0002: Producir XAdES-BES 1.3.2 con DSS Baseline-B

- Estado: Aceptado, sujeto a certificación SRI
- Fecha: 2026-10-20

## Contexto

La ficha SRI pide XAdES-BES con namespace 1.3.2, `SigningCertificate` v1, RSA-SHA1, SHA-1 y
`KeyInfo` firmado. DSS 6.5.1 recomienda los niveles Baseline y su enum `SignatureLevel.XAdES_BES`
no completa el flujo de extensión (`UnsupportedOperationException` al intentar extender).

## Decisión

Usar `SignatureLevel.XAdES_BASELINE_B` con:

- `setEn319132(false)` para emitir elementos XAdES 1.3.2 y `SigningCertificate` v1.
- `setDigestAlgorithm(SHA1)`, `setReferenceDigestAlgorithm(SHA1)` y
  `setSigningCertificateDigestMethod(SHA1)`.
- `setSignKeyInfo(true)`.
- Canonicalización inclusiva (`REC-xml-c14n-20010315`) para `SignedInfo`, `SignedProperties` y
  `KeyInfo`.
- Una `DSSReference` explícita a `#<id-raíz>` con `EnvelopedSignatureTransform`, en lugar de la
  referencia por defecto `URI=""` con filtro XPath2.

## Consecuencias

- La firma contiene `SigningCertificate` (no `V2`) y una referencia enveloped clásica, más cercana
  al Anexo 14 de la ficha.
- El `SignatureLevel` nominal es Baseline-B, pero la estructura emitida es compatible con BES.
- **Riesgo:** el validador del SRI puede interpretar el perfil de forma estricta. Es obligatorio
  validar en certificación antes de producción.
- No se deben cambiar estos parámetros sin un ADR y pruebas de estructura.
