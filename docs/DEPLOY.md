# Despliegue

## Requisitos

- Java 21 y Maven 3.9+.
- AWS CLI v2 autenticado.
- AWS SAM CLI.
- Una cuenta AWS con permisos para CloudFormation, IAM, Lambda, API Gateway, Secrets Manager y S3
  (SAM usa S3 para artefactos).
- Un PKCS#12 válido con clave privada RSA y cadena de certificados.

No uses el certificado de producción en pruebas locales. Crea un certificado de prueba y un
secreto separado.

## 1. Compilar y verificar

```bash
export JAVA_HOME=/ruta/a/jdk-21
mvn -B verify
./scripts/package-lambda.sh --skip-build
```

El resultado es `target/lambda.zip`. Verifica su contenido:

```bash
unzip -l target/lambda.zip | head
```

Debe contener un único JAR shaded en la raíz.

## 2. Crear el secreto

El secreto es JSON con el PKCS#12 en base64:

```json
{
  "p12Base64": "<base64 del archivo .p12>",
  "password": "<contraseña>",
  "alias": "opcional"
}
```

Puedes usar el script auxiliar, que pide la contraseña por consola y evita dejarla en el historial:

```bash
./scripts/create-secret.sh sri-xades-signer/cert ./certificado.p12
```

O hacerlo manualmente:

```bash
P12_B64="$(base64 < certificado.p12 | tr -d '\n')"
read -r -s -p "Password PKCS#12: " P12_PASSWORD
printf '{"p12Base64":"%s","password":"%s"}' "$P12_B64" "$P12_PASSWORD" > /tmp/secret.json
chmod 600 /tmp/secret.json
aws secretsmanager create-secret \
  --name sri-xades-signer/cert \
  --secret-string file:///tmp/secret.json
rm -f /tmp/secret.json
unset P12_B64 P12_PASSWORD
```

Guarda el ARN que devuelve el comando. El rol de la Lambda solo tendrá permiso sobre ese ARN.

Si usas una CMK de KMS, pasa su ARN en `CertificateKmsKeyArn` al desplegar; la plantilla agrega
`kms:Decrypt` condicionalmente.

## 3. Desplegar

```bash
sam deploy \
  --template-file infra/template.yaml \
  --stack-name sri-xades-signer \
  --capabilities CAPABILITY_IAM \
  --resolve-s3 \
  --parameter-overrides \
    CertificateSecretArn=arn:aws:secretsmanager:us-east-1:123456789012:secret:sri-xades-signer/cert-xxxxx \
    Stage=cert
```

Si usas una CMK:

```bash
sam deploy ... \
  --parameter-overrides \
    CertificateSecretArn=<arn> \
    CertificateKmsKeyArn=<arn-cmk> \
    Stage=cert
```

La plantilla usa `Stage=cert` por defecto. No uses `Stage=prod` hasta completar la validación en el
ambiente de certificación del SRI.

La plantilla crea:

- `AWS::Serverless::Function` Java 21, arquitectura `arm64`.
- `AWS::Serverless::Api` regional con `AWS_IAM`.
- Permiso `secretsmanager:GetSecretValue` sobre el ARN indicado.
- Concurrencia reservada y throttling.
- Log group con retención de 30 días.

Obtén la URL:

```bash
aws cloudformation describe-stacks \
  --stack-name sri-xades-signer \
  --query "Stacks[0].Outputs[?OutputKey=='SignEndpoint'].OutputValue" \
  --output text
```

## 4. Probar

Necesitas credenciales con `execute-api:Invoke`. Ejemplo con `curl` y SigV4:

```bash
export SIGNER_URL="$(aws cloudformation describe-stacks \
  --stack-name sri-xades-signer \
  --query "Stacks[0].Outputs[?OutputKey=='SignEndpoint'].OutputValue" \
  --output text)"

curl --request POST "$SIGNER_URL" \
  --aws-sigv4 "aws:amz:us-east-1:execute-api" \
  --user "$AWS_ACCESS_KEY_ID:$AWS_SECRET_ACCESS_KEY" \
  --header "x-amz-security-token: $AWS_SESSION_TOKEN" \
  --header "Content-Type: application/xml" \
  --data-binary @comprobante-prueba.xml \
  --output comprobante-firmado.xml

xmllint --noout comprobante-firmado.xml
```

Verifica que el XML contenga `SigningCertificate` (no `SigningCertificateV2`), `SignedProperties`,
`rsa-sha1` y una referencia `URI="#comprobante"`.

## 5. Actualizar el código

```bash
mvn -B verify
./scripts/package-lambda.sh --skip-build
sam deploy \
  --template-file infra/template.yaml \
  --stack-name sri-xades-signer \
  --capabilities CAPABILITY_IAM \
  --resolve-s3 \
  --parameter-overrides \
    CertificateSecretArn=<arn> \
    Stage=cert
```

## 6. Rotar el certificado

1. Crea el nuevo PKCS#12 y valida que la clave coincida con el certificado.
2. Actualiza el secreto:

   ```bash
   ./scripts/create-secret.sh sri-xades-signer/cert ./nuevo-certificado.p12 --update
   ```

3. Espera `CERTIFICATE_CACHE_SECONDS` (300 s por defecto) o fuerza un cold start actualizando
   una variable de entorno.
4. Firma un comprobante de prueba y valida la cadena.
5. Revoca el certificado anterior según tu entidad certificadora.

No cambies el ARN del secreto si no es necesario; así evitas modificar la política IAM.

## 7. Logs y diagnóstico

```bash
sam logs --stack-name sri-xades-signer --tail
```

Los logs incluyen `requestId` y clase de error, no el XML, la contraseña ni el PKCS#12.
Para diagnosticar un fallo, correlaciona `X-Request-Id` con CloudWatch.

## 8. Reversión

- Revisa el historial de versiones de la función Lambda y publica la versión anterior.
- Si el problema es el certificado, actualiza el secreto al valor anterior.
- Si el problema es la plantilla, despliega la versión anterior del repositorio con SAM.

## 9. Costos y límites

- Lambda se factura por invocación y GB-segundo.
- API Gateway se factura por millón de solicitudes.
- Secrets Manager se factura por secreto y por 10 000 llamadas a la API.
- La concurrencia reservada limita el costo y el abuso.
- El caché en memoria reduce llamadas a Secrets Manager.

## 10. Antes de producción

Completa la [checklist de certificación SRI](SRI-CERTIFICATION.md). No declares producción antes
de que el SRI acepte el XML firmado y de que exista un procedimiento de rotación y monitoreo.
