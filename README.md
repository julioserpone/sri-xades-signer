# SRI XAdES-BES Signer

Firmador **XAdES-BES enveloped** para comprobantes electrónicos del Servicio de Rentas Internas (SRI) de Ecuador, empaquetable como **AWS Lambda**.

[![CI](https://github.com/julioserpone/sri-xades-signer/actions/workflows/ci.yml/badge.svg)](https://github.com/julioserpone/sri-xades-signer/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

> **Estado: experimental, no certificado para producción.**
>
> Este proyecto genera firmas con el perfil solicitado por la ficha SRI, pero **todavía no ha sido validado en el ambiente de certificación del SRI**. No lo declares apto para producción ni lo uses con comprobantes reales hasta completar esa validación. Consulta [docs/SRI-CERTIFICATION.md](docs/SRI-CERTIFICATION.md).

## Qué hace

Recibe **un** XML de comprobante y devuelve el mismo XML con una firma XAdES-BES enveloped añadida:

- XAdES `http://uri.etsi.org/01903/v1.3.2#`.
- `SigningCertificate` (v1) con digest SHA-1.
- `SignatureMethod` `rsa-sha1` y referencias SHA-1.
- `SignedProperties` protegidas.
- `KeyInfo` incluido y firmado.
- Transformada `enveloped-signature` sobre el `id` de la raíz.
- Codificación UTF-8.
- Certificado PKCS#12 cargado desde **AWS Secrets Manager**, nunca desde el repositorio.

El firmador **no** implementa autenticación propia: el endpoint debe publicarse detrás de **API Gateway con autenticación IAM**. No expongas un endpoint público sin autenticación; sería un oráculo de firma.

## Requisitos

- Java 21 y Maven 3.9+ **solo si vas a compilar**; para desplegar el ZIP de un release no
  necesitas Java.
- Una cuenta AWS para desplegar.
- AWS SAM CLI y AWS CLI v2 para el despliegue.
- Un certificado PKCS#12 (`.p12`) con clave privada RSA y cadena de certificados, válido para firma electrónica.

En macOS con Homebrew:

```bash
brew install openjdk@21 maven
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

## Compilar y probar

```bash
export JAVA_HOME=/ruta/a/jdk-21
mvn -B verify
```

`verify` ejecuta compilación, pruebas, formato (Spotless) y análisis estático (SpotBugs).

Para formatear:

```bash
mvn -B spotless:apply
```

## Paquete para AWS Lambda

```bash
./scripts/package-lambda.sh
```

El script ejecuta `mvn clean verify`, arma `target/lambda.zip` y muestra su SHA-256. El ZIP contiene el JAR shaded en la raíz, listo para:

```bash
aws lambda update-function-code \
  --function-name sri-xades-signer \
  --zip-file fileb://target/lambda.zip
```

También puedes usar la imagen de contenedor opcional:

```bash
docker build -t sri-xades-signer .
```

## Despliegue

La plantilla usa `Stage=cert` por defecto. No uses `Stage=prod` hasta completar la validación en
el ambiente de certificación del SRI. Crea:

- Una función Lambda Java 21.
- Una API Gateway regional con autorización **AWS_IAM**.
- Permisos mínimos para leer el secreto y escribir logs.
- Límite de concurrencia y throttling.

Pasos resumidos:

1. Crear el secreto con el PKCS#12 y su contraseña.
2. Descargar `lambda.zip` desde [Releases](https://github.com/julioserpone/sri-xades-signer/releases)
   o empaquetarlo localmente.
3. Desplegar `infra/template.yaml` con SAM.
4. Probar con firma SigV4.

Si es tu primera vez en AWS, sigue [docs/AWS-SETUP.md](docs/AWS-SETUP.md): incluye usuario
IAM, perfil de AWS CLI, certificado de prueba, secreto, despliegue, prueba del endpoint y
limpieza. Consulta [docs/DEPLOY.md](docs/DEPLOY.md) para el procedimiento detallado y
[docs/API.md](docs/API.md) para el contrato HTTP.

## Contrato HTTP

```http
POST /v1/sign
Content-Type: application/xml
Authorization: AWS4-HMAC-SHA256 ...
```

Cuerpo: XML UTF-8 del comprobante, con `id` en el elemento raíz y sin firma previa.

Respuesta `200`:

```http
Content-Type: application/xml; charset=utf-8
X-Request-Id: ...
```

Cuerpo: el XML firmado.

Ejemplo con `curl` y SigV4 (requiere `curl` reciente):

```bash
curl --request POST "$SIGNER_URL/v1/sign" \
  --aws-sigv4 "aws:amz:us-east-1:execute-api" \
  --user "$AWS_ACCESS_KEY_ID:$AWS_SECRET_ACCESS_KEY" \
  --header "x-amz-security-token: $AWS_SESSION_TOKEN" \
  --header "Content-Type: application/xml" \
  --data-binary @comprobante.xml \
  --output comprobante-firmado.xml
```

Si no usas credenciales temporales, omite el header `x-amz-security-token`.

Errores: `400`, `405`, `413`, `415` y `500` con JSON `{"error":"...","requestId":"..."}`. Ver [docs/API.md](docs/API.md).

## Uso local (sin AWS)

```bash
java -jar target/sri-xades-signer-*.jar \
  --in comprobante.xml \
  --out comprobante-firmado.xml \
  --p12 certificado.p12 \
  --password-env P12_PASSWORD
```

La contraseña se lee de una variable de entorno. `--password` existe solo para pruebas locales y no debe usarse en automatización real.

## Seguridad

- La clave privada vive en **AWS Secrets Manager**; no se acepta por variable de entorno ni por cuerpo HTTP.
- El secreto esperado es JSON:

  ```json
  {"p12Base64":"...","password":"...","alias":"opcional"}
  ```

- La función usa un rol IAM con `secretsmanager:GetSecretValue` sobre un ARN concreto.
- El parser XML bloquea DTD, entidades externas, XInclude y esquemas externos.
- Se limita el tamaño del cuerpo y se restringe la raíz a comprobantes SRI.
- Se rechazan documentos que ya tengan firma.
- No se registra el XML, la contraseña ni el contenido del PKCS#12.
- El endpoint debe tener autenticación IAM y, preferiblemente, WAF y límites de uso.

Lee [SECURITY.md](SECURITY.md) y [docs/THREAT-MODEL.md](docs/THREAT-MODEL.md).

## Estructura

```text
src/main/java/com/julioserpone/srixadessigner/
  XadesBesSigner.java              Firma XAdES-BES con DSS
  config/SignerConfig.java         Límites y opciones por entorno
  crypto/                          PKCS#12 y Secrets Manager
  lambda/SignHandler.java          Handler API Gateway
  xml/                             Parser endurecido y verificación
infra/template.yaml                SAM: Lambda + API Gateway IAM
scripts/                           Empaquetado y configuración de GitHub
docs/                              API, despliegue, ADR y certificación
```

## Documentación

- [API](docs/API.md)
- [Despliegue](docs/DEPLOY.md)
- [Certificación SRI](docs/SRI-CERTIFICATION.md)
- [Modelo de amenazas](docs/THREAT-MODEL.md)
- [Reglas de GitHub](docs/GITHUB-RULES.md)
- [ADRs](docs/adr/)

## Contribuir

Lee [CONTRIBUTING.md](CONTRIBUTING.md). `main` está protegida: se requiere pull request y el check `ci-success` en verde. No se aceptan cambios que debiliten el perfil XAdES, el manejo de secretos o la autenticación.

## Licencia

Código propio bajo [Apache-2.0](LICENSE). Las dependencias conservan sus licencias; consulta [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

Este proyecto no está afiliado al SRI ni al Banco Central del Ecuador.
