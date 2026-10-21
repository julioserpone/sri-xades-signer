# API

Este documento describe el contrato HTTP legacy (API Gateway + Secrets Manager). Para el modo
multicliente recomendado para Laravel, que invoca la Lambda directamente y lee el PKCS#12 desde
S3, consulta [LAMBDA-INVOKE.md](LAMBDA-INVOKE.md).

## Endpoint

```http
POST /v1/sign
```

La URL completa la entrega el stack SAM:

```text
https://<api-id>.execute-api.<region>.amazonaws.com/<stage>/v1/sign
```

## Autenticación

La API usa **AWS IAM**. El cliente debe firmar la solicitud con **AWS Signature Version 4**
(SigV4) y tener permiso `execute-api:Invoke` sobre el recurso.

No se usa API key. No publiques el endpoint sin autorización: el firmador es un servicio sensible.

## Request

### Headers

| Header | Obligatorio | Valor |
|---|---|---|
| `Content-Type` | Sí | `application/xml` o `text/xml` (se ignora `charset`) |
| `Authorization` | Sí | Firma SigV4 |
| `X-Amz-Security-Token` | Si usas credenciales temporales | Token de sesión |

### Body

Un solo documento XML UTF-8 del comprobante.

Requisitos:

- Elemento raíz permitido:
  - `factura`
  - `notaCredito`
  - `notaDebito`
  - `comprobanteRetencion`
  - `guiaRemision`
  - `liquidacionCompra`
- El elemento raíz debe tener un atributo `id` (o `Id`) no vacío. El SRI usa `id="comprobante"`.
- El documento no debe contener una firma previa (`ds:Signature`).
- No debe contener `DOCTYPE`, entidades externas, XInclude ni declaraciones de esquema externas.
- Tamaño máximo por defecto: 1 048 576 bytes (1 MiB). Configurable con `MAX_XML_BYTES`.

El handler acepta body directo o `isBase64Encoded=true` (API Gateway puede marcarlo así).

### Ejemplo

```bash
curl --request POST "$SIGNER_URL/v1/sign" \
  --aws-sigv4 "aws:amz:us-east-2:execute-api" \
  --user "$AWS_ACCESS_KEY_ID:$AWS_SECRET_ACCESS_KEY" \
  --header "x-amz-security-token: $AWS_SESSION_TOKEN" \
  --header "Content-Type: application/xml" \
  --data-binary @comprobante.xml \
  --output comprobante-firmado.xml
```

Si no usas credenciales temporales, omite `x-amz-security-token`. Ajusta la región y el servicio
de la firma SigV4 (`execute-api`) según tu endpoint.

## Response

### Éxito

```http
HTTP/1.1 200 OK
Content-Type: application/xml; charset=utf-8
Cache-Control: no-store
X-Content-Type-Options: nosniff
X-Request-Id: <request-id>
```

Cuerpo: el mismo comprobante con un `ds:Signature` XAdES-BES enveloped añadido.

Propiedades de la firma:

- Namespace XAdES `http://uri.etsi.org/01903/v1.3.2#`.
- `ds:SignatureMethod` `http://www.w3.org/2000/09/xmldsig#rsa-sha1`.
- `ds:DigestMethod` SHA-1 en todas las referencias.
- `xades:SigningCertificate` v1 con digest SHA-1.
- `xades:SignedProperties` referenciadas y protegidas.
- `ds:KeyInfo` incluido y firmado.
- Referencia al `id` de la raíz con transformada `enveloped-signature`.
- Codificación UTF-8.

### Errores

Todos los errores devuelven JSON:

```json
{
  "error": "invalid_xml",
  "requestId": "abc-123"
}
```

| HTTP | `error` | Causa |
|---:|---|---|
| 400 | `invalid_xml` | XML vacío, mal formado, DTD, raíz no permitida, sin `id`, body base64 inválido o documento ya firmado |
| 405 | `method_not_allowed` | Método distinto de `POST` |
| 413 | `payload_too_large` | Body mayor que `MAX_XML_BYTES` |
| 415 | `unsupported_media_type` | Falta `Content-Type` o no es XML |
| 500 | `certificate_unavailable` | No se pudo leer o cargar el secreto/PKCS#12 |
| 500 | `signing_failed` | Falló la firma o la verificación posterior |
| 500 | `internal_error` | Error inesperado |

Los errores 500 no incluyen detalles internos ni material criptográfico. Usa `X-Request-Id` para
correlacionar con CloudWatch.

## Límites y configuración

| Variable | Default | Descripción |
|---|---:|---|
| `MAX_XML_BYTES` | `1048576` | Tamaño máximo del XML |
| `ALLOWED_ROOTS` | lista SRI | Raíces permitidas, separadas por coma |
| `VERIFY_AFTER_SIGN` | `true` | Verifica la firma antes de responder |
| `REQUIRE_CERTIFICATE_VALIDITY` | `true` | Rechaza certificados vencidos o aún no válidos |
| `CERTIFICATE_SECRET_ID` | — | ARN o nombre del secreto de Secrets Manager |
| `CERTIFICATE_ALIAS` | — | Alias de la clave privada en el PKCS#12 |
| `CERTIFICATE_CACHE_SECONDS` | `300` | TTL de caché del certificado en memoria |

La plantilla SAM configura throttling en API Gateway y permite concurrencia reservada opcional (`ReservedConcurrency=0` por defecto). Ajusta esos valores según tu volumen,
pero mantén el endpoint autenticado y con límites.

## Ejemplo de verificación

Puedes validar el XML firmado con la biblioteca DSS, con `xmlsec1` o con la herramienta de
validación del SRI. La verificación de producción debe hacerse en el ambiente de certificación
del SRI; este proyecto no la sustituye.

```bash
xmllint --noout comprobante-firmado.xml
```

No envíes comprobantes reales a entornos de prueba públicos ni compartas certificados en issues.
