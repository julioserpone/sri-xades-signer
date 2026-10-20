---
name: sri-xades-signer
description: Trabaja en el firmador XAdES-BES para el SRI de Ecuador, su Lambda, su API IAM y su empaquetado. Úsala para cambios de firma, seguridad, despliegue, pruebas o documentación de este repositorio.
---

# Skill: SRI XAdES-BES Signer

## Cuándo usar

Usa esta skill cuando la tarea implique:

- El perfil XAdES-BES del SRI (1.3.2, RSA-SHA1, `SigningCertificate` v1, `KeyInfo`).
- La Lambda, API Gateway IAM o Secrets Manager.
- PKCS#12, carga de certificados o rotación de secretos.
- Empaquetado `target/lambda.zip`, SAM o Docker.
- Pruebas de firma, parser seguro o verificación XMLDSig.
- Documentación de certificación, seguridad o despliegue.

## Reglas

1. Lee `AGENTS.md` y `README.md`.
2. No cambies el perfil de firma sin ADR y pruebas.
3. No agregues secretos ni certificados al repositorio.
4. No expongas el endpoint sin IAM.
5. No debilites el parser, el tamaño máximo, las raíces permitidas ni el rechazo de doble firma.
6. No afirmes certificación SRI sin evidencia.

## Comandos

```bash
export JAVA_HOME=/ruta/a/jdk-21
mvn -B verify
mvn -B spotless:apply
./scripts/package-lambda.sh
```

## Verificación mínima

- `mvn -B verify` en verde.
- Prueba de firma con `SigningCertificate` v1 y sin `SigningCertificateV2`.
- Prueba de referencia `URI="#comprobante"` con transformada enveloped.
- Prueba del handler para 200/400/405/413/415.
- `target/lambda.zip` contiene el JAR shaded.
- Documentación actualizada y sin declaraciones falsas de producción.
