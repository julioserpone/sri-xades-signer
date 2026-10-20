# AGENTS.md

Instrucciones para agentes de IA y personas que trabajen en este repositorio.

## Propósito

Este repositorio implementa un firmador **XAdES-BES enveloped** para comprobantes electrónicos
del SRI de Ecuador, empaquetado como AWS Lambda. El contrato de firma debe mantenerse compatible
con la ficha SRI y con el perfil validado en certificación.

## Invariantes que no se negocian

1. **Perfil de firma:** XAdES-BES enveloped, namespace `http://uri.etsi.org/01903/v1.3.2#`,
   `SigningCertificate` v1, RSA-SHA1, referencias SHA-1, UTF-8 y `KeyInfo` firmado.
2. **Una firma por llamada:** el cuerpo es un solo XML de comprobante. No agregues batch,
   firmas múltiples ni un endpoint que firme contenido arbitrario.
3. **Sin oráculo público:** el endpoint debe usar autenticación IAM. No agregues rutas públicas,
   claves API embebidas ni bypass de autenticación.
4. **Secretos:** la clave privada y la contraseña del PKCS#12 viven en AWS Secrets Manager.
   Nunca las pongas en el repositorio, en variables de entorno, en logs, en mensajes de error,
   en pruebas o en artefactos.
5. **Parser endurecido:** no habilites DTD, entidades externas, XInclude, esquemas externos ni
   expansión de entidades. No elimines los límites de tamaño, la lista de raíces permitidas ni
   el rechazo de documentos ya firmados.
6. **Certificación:** no declares el proyecto “listo para producción” ni “certificado por el SRI”
   hasta que exista evidencia de validación en el ambiente de certificación. Mantén el aviso de
   estado experimental en README y docs.
7. **Dependencias:** no cambies DSS, el algoritmo de firma ni el perfil sin un ADR y sin actualizar
   las pruebas de estructura y validación criptográfica.

## Comandos de referencia

```bash
export JAVA_HOME=/ruta/a/jdk-21
mvn -B verify                 # compila, prueba, formatea y analiza
mvn -B spotless:apply         # aplica formato
./scripts/package-lambda.sh   # verifica y genera target/lambda.zip
```

El check obligatorio de `main` es `ci-success`.

## Estructura

- `src/main/java/com/julioserpone/srixadessigner/XadesBesSigner.java`: configuración DSS,
  referencia al `id` de la raíz, transformada enveloped y firma.
- `src/main/java/com/julioserpone/srixadessigner/crypto/`: PKCS#12 y Secrets Manager.
- `src/main/java/com/julioserpone/srixadessigner/xml/`: parser seguro y verificación XMLDSig.
- `src/main/java/com/julioserpone/srixadessigner/lambda/SignHandler.java`: contrato API Gateway.
- `infra/template.yaml`: Lambda + API Gateway IAM + permisos.
- `docs/`: API, despliegue, ADR, modelo de amenazas y certificación.
- `scripts/`: empaquetado y configuración de GitHub.

## Protocolo de cambio

1. Lee los archivos relevantes antes de editar; no reescribas módulos completos sin necesidad.
2. Mantén el estilo existente (Google Java Format, 2 espacios, nombres en inglés en código).
3. Agrega o actualiza pruebas para cualquier cambio de comportamiento.
4. Ejecuta `mvn -B verify` y, si tocaste empaquetado, `./scripts/package-lambda.sh --skip-build`.
5. Actualiza `README.md`, `docs/API.md`, `CHANGELOG.md` y el ADR correspondiente cuando cambie
   el contrato, la seguridad o el perfil de firma.
6. Abre un pull request. `main` requiere PR, historial lineal y `ci-success` en verde.
7. No hagas `git push --force` a `main`, no desactives reglas y no agregues secretos.

## Revisión de seguridad

Antes de aprobar un cambio, verifica:

- ¿Se puede usar el endpoint sin IAM?
- ¿Se registra o devuelve el XML, la contraseña, el PKCS#12 o la clave privada?
- ¿Se debilitó el parser, el límite de tamaño o la validación de raíz?
- ¿Se puede firmar un documento ya firmado o un documento de otro tipo?
- ¿El perfil sigue siendo XAdES-BES 1.3.2 con SHA-1 y `SigningCertificate` v1?
- ¿El paquete Lambda sigue siendo reproducible y desplegable?

## Para agentes de IA

- No inventes resultados de certificación del SRI.
- No uses credenciales reales ni datos tributarios reales en pruebas.
- No ejecutes comandos destructivos (`rm -rf`, borrar ramas, reescribir historial) sin confirmación.
- Si una tarea es ambigua o afecta el perfil de firma, detente y pregunta.
- Prefiere cambios pequeños, verificables y con pruebas.
