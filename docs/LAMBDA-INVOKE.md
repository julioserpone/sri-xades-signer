# Invocación directa de Lambda con PKCS#12 en S3

Este modo está pensado para un backend Laravel/PHP que atiende a varios clientes. El backend
invoca una sola función Lambda con el UUID del inquilino, la contraseña de su PKCS#12 y el XML.
La Lambda lee el certificado desde un bucket S3 privado y devuelve el XML firmado.

No usa API Gateway ni Secrets Manager. Sigue siendo un servicio de firma sensible: la función
debe invocarse solo con IAM y el password nunca debe registrarse.

## Arquitectura

```text
Laravel/PHP
  └─ AWS SDK (IAM) ── Lambda Invoke ──> Firmador Java
                                         ├─ valida UUID
                                         ├─ lee s3://<bucket>/tenants/<uuid>/certificate.p12
                                         ├─ abre el PKCS#12 con el password recibido
                                         └─ firma XAdES-BES
```

- Una función Lambda compartida para todos los inquilinos.
- Un bucket S3 privado, con SSE-S3 y versionado.
- Una clave por inquilino: `tenants/<uuid>/certificate.p12`.
- Un usuario o rol IAM de integración para el backend, no uno por inquilino.
- La contraseña se guarda cifrada en la base de datos del backend y se descifra solo al firmar.
- La Lambda cachea la identidad por inquilino; un hash SHA-256 del password se verifica en cada
  invocación para no firmar con un password incorrecto.

## Despliegue

Compila el ZIP:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn -B verify
./scripts/package-lambda.sh --skip-build
```

Despliega la plantilla de invocación directa:

```bash
sam deploy \
  --template-file infra/template-lambda-invoke.yaml \
  --stack-name sri-xades-signer-invoke \
  --capabilities CAPABILITY_IAM \
  --resolve-s3 \
  --parameter-overrides \
    Stage=cert \
    ReservedConcurrency=0
```

Si quieres usar el ZIP de un release en lugar de compilar, descárgalo en `target/lambda.zip`
antes del despliegue.

Obtén el nombre de la función y el bucket:

```bash
aws cloudformation describe-stacks \
  --stack-name sri-xades-signer-invoke \
  --query "Stacks[0].Outputs" \
  --output table
```

## Alta de un inquilino

1. Genera o recibe el `.p12` del cliente y valida que la clave privada corresponda al
   certificado.
2. Asigna un UUID interno al inquilino. No uses el RUC como identificador de AWS.
3. Sube el archivo con cifrado del lado del servidor:

```bash
TENANT_UUID="123e4567-e89b-12d3-a456-426614174000"

aws s3 cp /ruta/segura/certificado.p12 \
  "s3://<bucket>/tenants/${TENANT_UUID}/certificate.p12" \
  --sse AES256
```

4. Guarda la contraseña cifrada en la base de datos del backend. No la guardes en S3, en el
   repositorio ni en variables de entorno del proceso PHP.
5. Para rotar el certificado, sube el nuevo archivo a la misma clave. La Lambda puede tardar
   hasta `CertificateCacheSeconds` (300 s por defecto) en usar la nueva versión; fuerza un cold
   start o espera ese TTL.

## Contrato de invocación

La Lambda usa `InvocationType=RequestResponse` y recibe un JSON:

```json
{
  "tenantUuid": "123e4567-e89b-12d3-a456-426614174000",
  "password": "<password del PKCS#12>",
  "xmlBase64": "<XML UTF-8 en base64>"
}
```

Respuesta correcta:

```json
{
  "signedXmlBase64": "<XML firmado en base64>",
  "requestId": "..."
}
```

Error:

```json
{
  "error": "certificate_unavailable",
  "requestId": "..."
}
```

Códigos de error:

| Código | Significado |
| --- | --- |
| `invalid_request` | Falta un campo, el UUID es inválido, el base64 es inválido o el XML no es válido. |
| `payload_too_large` | El XML supera `MaxXmlBytes`. |
| `certificate_unavailable` | El objeto S3 no existe, el PKCS#12 no se puede abrir o el password es incorrecto. |
| `signing_failed` | El certificado no es válido para firmar o falló la firma. |
| `internal_error` | Error inesperado. Correlaciona `requestId` con CloudWatch. |

La respuesta no incluye detalles internos ni el password. Los logs solo contienen `requestId` y
la clase de error.

### Ejemplo con AWS CLI

```bash
XML_B64="$(base64 < comprobante.xml | tr -d '\n')"

cat > /tmp/sign-request.json <<EOF
{
  "tenantUuid": "${TENANT_UUID}",
  "password": "${P12_PASSWORD}",
  "xmlBase64": "${XML_B64}"
}
EOF

aws lambda invoke \
  --function-name sri-xades-signer-invoke \
  --cli-binary-format raw-in-base64-out \
  --payload file:///tmp/sign-request.json \
  /tmp/sign-response.json

python3 - <<'PY'
import base64, json
response = json.load(open('/tmp/sign-response.json'))
if 'error' in response:
    raise SystemExit(response['error'])
open('comprobante-firmado.xml', 'wb').write(base64.b64decode(response['signedXmlBase64']))
PY

rm -f /tmp/sign-request.json
unset XML_B64 P12_PASSWORD
```

`/tmp/sign-request.json` contiene el password en claro. Si usas este ejemplo, bórralo de
inmediato y no lo dejes en un equipo compartido.

## IAM del backend

El usuario o rol del backend necesita únicamente:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "lambda:InvokeFunction",
      "Resource": "arn:aws:lambda:us-east-2:123456789012:function:sri-xades-signer-invoke"
    }
  ]
}
```

Si el backend también sube certificados, agrega `s3:PutObject` sobre
`arn:aws:s3:::<bucket>/tenants/*`. No le des `s3:GetObject` al backend: solo la Lambda necesita
leer los PKCS#12. Tampoco necesita `secretsmanager:*`.

No crees un usuario IAM por inquilino. El aislamiento se controla en el backend, que decide qué
UUID y qué password envía; la Lambda solo puede leer objetos bajo el prefijo `tenants/`.

## Variables de entorno

| Variable | Default | Uso |
| --- | --- | --- |
| `CERTIFICATE_S3_BUCKET` | — | Bucket privado de certificados. |
| `CERTIFICATE_S3_PREFIX` | `tenants/` | Prefijo de las claves. |
| `CERTIFICATE_ALIAS` | vacío | Alias del PKCS#12 si tiene varias claves. |
| `MAX_XML_BYTES` | `1048576` | Tamaño máximo del XML. |
| `MAX_CERTIFICATE_BYTES` | `1048576` | Tamaño máximo del PKCS#12. |
| `CERTIFICATE_CACHE_SECONDS` | `300` | TTL de la identidad en memoria; `0` desactiva el caché. |
| `CERTIFICATE_CACHE_MAX_ENTRIES` | `100` | Máximo de inquilinos cacheados por instancia. |
| `VERIFY_AFTER_SIGN` | `true` | Verifica la firma generada antes de responder. |
| `REQUIRE_CERTIFICATE_VALIDITY` | `true` | Exige que el certificado esté vigente. |

## Seguridad y costos

- S3 es privado, bloquea acceso público y rechaza solicitudes sin TLS.
- SSE-S3 no tiene costo adicional; una CMK de KMS queda para una etapa posterior.
- El password viaja en la invocación TLS/IAM y no se escribe en logs.
- La Lambda limpia el arreglo de caracteres del password después de usarlo.
- El caché guarda la clave privada en memoria; no se persiste en disco.
- Lambda no cobra por estar creada. Una función compartida es más simple y barata que una
  función por inquilino.
- DynamoDB puede evaluarse más adelante si se quiere centralizar metadatos; no es necesario para
  este MVP.
- Este modo sigue siendo experimental hasta validar el XML en el ambiente de certificación del
  SRI.
