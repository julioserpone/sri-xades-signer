# Configuración de AWS desde cero

Esta guía está pensada para una cuenta AWS nueva y para desplegar el firmador en el
ambiente de **pruebas/certificación**. No uses el certificado ni el usuario de producción
para estas pruebas.

> Para el modo multicliente con un PKCS#12 por inquilino en S3 e invocación directa de Lambda,
> consulta [LAMBDA-INVOKE.md](LAMBDA-INVOKE.md). Esta guía describe el modo legacy con Secrets
> Manager y API Gateway.

## 1. Decisiones iniciales

- **Región:** usa `us-east-2` en todos los comandos. Si eliges otra, cámbiala de forma
  consistente en AWS CLI, SAM y el endpoint.
- **Etapa:** usa `Stage=dev` para un certificado autofirmado de prueba y `Stage=cert`
  cuando tengas el certificado del ambiente de certificación del SRI.
- **Costo:** Secrets Manager tiene un costo mensual por secreto; Lambda, API Gateway, S3
  y CloudWatch tienen capas gratuitas, pero revisa la calculadora de AWS antes de dejar
  recursos encendidos.
- **Producción:** no uses `Stage=prod` hasta completar la validación del SRI.

> **Atajo:** si ya tienes el perfil `sri-xades` configurado, puedes crear el secreto y el
> stack de pruebas con `./scripts/aws-create-test-env.sh`. La sección 13 explica las
> opciones y el script de limpieza.

## 2. Protege la cuenta raíz

1. Entra a la consola de AWS con la cuenta raíz.
2. Activa **MFA** en la cuenta raíz.
3. No crees access keys para el usuario raíz.
4. Crea un usuario IAM para desplegar, por ejemplo `sri-xades-deployer`.

Para el primer despliegue, adjunta temporalmente estas políticas administradas:

- `PowerUserAccess`
- `IAMFullAccess`

`PowerUserAccess` permite crear Lambda, API Gateway, CloudFormation, S3, Secrets Manager y
CloudWatch. `IAMFullAccess` es necesario para que SAM cree y pase el rol de ejecución de la
Lambda. Son permisos amplios: úsalos solo en una cuenta de pruebas y elimina la clave de
acceso cuando termines. Para producción, crea permisos más específicos.

También puedes usar **IAM Identity Center** si tu cuenta ya lo tiene habilitado. En ese
caso no necesitas access keys permanentes; configura un perfil SSO y ejecuta
`aws sso login --profile sri-xades` antes de desplegar.

## 3. Instala las herramientas en macOS

```bash
brew install awscli aws-sam-cli openjdk@21 maven
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

Verifica:

```bash
aws --version
sam --version
java -version
mvn -version
```

`mvn -version` debe mostrar Java 21. Si muestra otra versión, revisa `JAVA_HOME`.

> En macOS con Apple Silicon, el AWS CLI instalado con Homebrew es nativo. Si aparece
> `bad CPU type in executable`, estás usando un binario x86 antiguo; desinstálalo e instala
> `awscli` con Homebrew como se indica arriba.

## 4. Configura el perfil de AWS CLI

Crea un usuario IAM con acceso programático y guarda la access key en un lugar seguro. No
pegues la clave en el repositorio, en un chat ni en un archivo dentro del proyecto.

```bash
aws configure --profile sri-xades
```

Responde:

- **AWS Access Key ID:** la clave del usuario `sri-xades-deployer`.
- **AWS Secret Access Key:** el secreto correspondiente.
- **Default region name:** `us-east-2`.
- **Default output format:** `json`.

Activa el perfil para esta terminal:

```bash
export AWS_PROFILE=sri-xades
aws sts get-caller-identity
```

El último comando debe devolver el `Account`, `Arn` y `UserId` de la cuenta. Si devuelve
`InvalidClientTokenId`, la clave es incorrecta o fue desactivada.

## 5. Prepara el certificado

### Opción A: certificado real de certificación del SRI

Usa el archivo `.p12` entregado para el ambiente de certificación. No uses el certificado
de producción. Guarda el archivo fuera del repositorio y no lo subas a Git.

### Opción B: certificado autofirmado solo para probar la Lambda

Sirve para validar el despliegue y el contrato HTTP, pero **no será aceptado por el SRI**:

```bash
openssl req -x509 -newkey rsa:2048 \
  -keyout /tmp/sri-test-key.pem \
  -out /tmp/sri-test-cert.pem \
  -days 30 -nodes \
  -subj "/CN=SRI Test"

openssl pkcs12 -export \
  -out /tmp/sri-test.p12 \
  -inkey /tmp/sri-test-key.pem \
  -in /tmp/sri-test-cert.pem \
  -name sri-test \
  -passout pass:CambiarEstaClave
```

## 6. Crea el secreto en Secrets Manager

El firmador espera un JSON con `p12Base64`, `password` y, opcionalmente, `alias`.

```bash
export AWS_PROFILE=sri-xades

./scripts/create-secret.sh \
  sri-xades-signer/test \
  /tmp/sri-test.p12 \
  --alias sri-test
```

El script pide la contraseña del PKCS#12 y muestra el ARN del secreto. Guárdalo en una
variable:

```bash
export SECRET_ARN="$(aws secretsmanager describe-secret \
  --secret-id sri-xades-signer/test \
  --region us-east-2 \
  --query ARN \
  --output text)"
echo "$SECRET_ARN"

if [ -z "$SECRET_ARN" ]; then
  echo "ERROR: SECRET_ARN está vacío. Revisa la región y el nombre del secreto." >&2
fi
```

El script no guarda la contraseña en el repositorio ni en el historial de shells. No
imprimas el contenido del secreto.

## 7. Obtén el paquete Lambda

### Opción A: descargar el release (no necesitas compilar Java)

Con GitHub CLI:

```bash
mkdir -p target
gh release download v0.1.3 \
  --repo julioserpone/sri-xades-signer \
  --pattern lambda.zip \
  --dir target
```

También puedes descargarlo desde la página de releases del repositorio. El ZIP contiene el
JAR shaded dentro de `lib/` y no requiere Maven ni Java para desplegarse.

### Opción B: compilar localmente

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
./scripts/package-lambda.sh
```

El resultado es `target/lambda.zip`.

## 8. Despliega con SAM

```bash
export AWS_PROFILE=sri-xades
export AWS_REGION=us-east-2

sam deploy \
  --stack-name sri-xades-signer-dev \
  --template-file infra/template.yaml \
  --capabilities CAPABILITY_IAM \
  --resolve-s3 \
  --region "$AWS_REGION" \
  --profile sri-xades \
  --parameter-overrides \
    Stage=dev \
    CertificateSecretArn="$SECRET_ARN" \
    ReservedConcurrency=0
```

`--resolve-s3` crea y usa un bucket gestionado por SAM para subir el ZIP. Si tu cuenta
bloquea la creación de buckets, crea uno y usa `--s3-bucket NOMBRE_DEL_BUCKET`.

`ReservedConcurrency=0` significa **sin reserva de concurrencia**. Es lo recomendado en
cuentas nuevas: AWS entrega 10 ejecuciones concurrentes por defecto y no permite reservar
si quedan menos de 10 libres. El throttling de API Gateway sigue limitando el abuso.
Cuando solicites un aumento de cuota, puedes usar `ReservedConcurrency=5` o el valor que
necesites.

La plantilla crea:

- Lambda Java 21 con arquitectura `arm64`.
- API Gateway regional con autenticación IAM.
- Rol de ejecución con permiso solo para leer el secreto indicado y escribir logs.
- Log group de CloudWatch y throttling en API Gateway; la concurrencia reservada es opcional.

Obtén la URL:

```bash
export SIGNER_URL="$(aws cloudformation describe-stacks \
  --stack-name sri-xades-signer-dev \
  --region us-east-2 \
  --query "Stacks[0].Outputs[?OutputKey=='SignEndpoint'].OutputValue" \
  --output text)"
echo "$SIGNER_URL"
```

## 9. Prueba el endpoint con SigV4

Crea un XML mínimo de prueba:

```bash
cat > /tmp/factura-prueba.xml <<'XML'
<factura id="comprobante">
  <infoTributaria>
    <razonSocial>PRUEBA LOCAL</razonSocial>
  </infoTributaria>
</factura>
XML
```

Carga las credenciales del perfil en variables temporales y llama al endpoint:

```bash
export AWS_PROFILE=sri-xades
eval "$(aws configure export-credentials --profile sri-xades --format env)"

CURL_AUTH=(--user "${AWS_ACCESS_KEY_ID}:${AWS_SECRET_ACCESS_KEY}")
if [ -n "${AWS_SESSION_TOKEN:-}" ]; then
  CURL_AUTH+=(--header "x-amz-security-token: ${AWS_SESSION_TOKEN}")
fi

curl --fail-with-body --silent --show-error \
  --request POST "$SIGNER_URL" \
  --aws-sigv4 "aws:amz:us-east-2:execute-api" \
  "${CURL_AUTH[@]}" \
  --header "Content-Type: application/xml" \
  --data-binary @/tmp/factura-prueba.xml \
  --output /tmp/factura-firmada.xml
```

Si usas access keys permanentes, la variable `AWS_SESSION_TOKEN` estará vacía y el header
no se envía. Si usas SSO o credenciales temporales, se envía automáticamente.

Verifica que la respuesta sea XML y contenga una firma:

```bash
xmllint --noout /tmp/factura-firmada.xml
grep -o '<ds:Signature' /tmp/factura-firmada.xml
```

La firma con certificado autofirmado sirve para comprobar el flujo técnico. La validación
real del SRI requiere el certificado y el ambiente de certificación.

## 10. Logs y diagnóstico

```bash
aws logs tail /aws/lambda/sri-xades-signer-dev --follow --profile sri-xades
```

Errores frecuentes:

| Síntoma | Causa probable | Solución |
| --- | --- | --- |
| `InvalidClientTokenId` | Access key incorrecta o desactivada | Revisa el perfil `sri-xades` |
| `bad CPU type in executable` | AWS CLI x86 en Apple Silicon | `brew install awscli` |
| `release version 21 not supported` | Maven usa otro JDK | Exporta `JAVA_HOME` de Java 21 |
| HTTP 403 | Falta SigV4 o `execute-api:Invoke` | Usa el curl de esta guía y revisa el usuario IAM |
| HTTP 415 | `Content-Type` incorrecto | Envía `application/xml` |
| HTTP 400 `invalid_xml` | XML sin `id` de raíz, raíz no permitida o ya firmado | Usa el XML de prueba y no lo firmes dos veces |
| HTTP 500 `certificate_unavailable` | Secreto, ARN, contraseña o alias incorrectos | Revisa Secrets Manager y los logs |
| `NoSuchBucket` | SAM no pudo crear/usar el bucket | Usa `--resolve-s3` o `--s3-bucket` |
| `Invalid value for '--parameter-overrides': CertificateSecretArn=` | `SECRET_ARN` está vacío o se consultó en otra región | Exporta el ARN con `--region us-east-2` y compruébalo con `echo "$SECRET_ARN"` |
| `ReservedConcurrentExecutions ... below its minimum value of [10]` | Cuenta nueva con límite de concurrencia | Despliega con `ReservedConcurrency=0` |
| HTTP 502 + `ClassNotFoundException` | ZIP antiguo con el JAR en la raíz | Usa `lambda.zip` de `v0.1.3` o regenera con `./scripts/package-lambda.sh` |

## 11. Limpieza

Cuando termines las pruebas:

```bash
sam delete \
  --stack-name sri-xades-signer-dev \
  --region us-east-2 \
  --profile sri-xades

aws secretsmanager delete-secret \
  --secret-id sri-xades-signer/test \
  --force-delete-without-recovery \
  --profile sri-xades
```

Eliminar el secreto es irreversible. No borres el certificado de certificación hasta
terminar la validación del SRI.

## 12. Qué necesita exactamente la integración

El cliente que firma comprobantes solo necesita:

1. Una URL de API Gateway con autenticación IAM.
2. Credenciales IAM con permiso `execute-api:Invoke` sobre esa API.
3. Enviar `POST /v1/sign` con `Content-Type: application/xml` y el XML UTF-8.
4. Recibir el XML firmado en el cuerpo de la respuesta.

La clave privada nunca sale de Secrets Manager hacia el cliente. La Lambda la carga en
memoria y la usa para firmar; no se escribe en logs ni se devuelve en la respuesta.

## 13. Scripts automáticos para macOS

Los scripts crean y eliminan el entorno de **pruebas**. No crean el usuario IAM ni sus
access keys; eso se hace una sola vez en los pasos 2 y 4.

### Crear o actualizar

Con el perfil `sri-xades` ya configurado:

```bash
./scripts/aws-create-test-env.sh
```

Por defecto:

- Usa `AWS_PROFILE=sri-xades`, `AWS_REGION=us-east-2` y `STAGE=dev`.
- Genera un certificado autofirmado de prueba en `target/test-cert.p12`.
- Crea o actualiza el secreto `sri-xades-signer/test`.
- Descarga `lambda.zip` de `v0.1.3`.
- Despliega el stack `sri-xades-signer-dev` con `ReservedConcurrency=0`.
- Guarda endpoint y ARN en `target/aws-test-env` (sin contenido sensible).
- Imprime la URL del endpoint.

Para usar un certificado real de certificación:

```bash
STAGE=cert \
./scripts/aws-create-test-env.sh \
  --p12 /ruta/certificado.p12 \
  --alias mi-alias
```

Con `STAGE=cert`, el stack por defecto es `sri-xades-signer-cert` y el secreto
`sri-xades-signer/cert`. Si no indicas `--p12`, el script se detiene.

Para un cliente o entorno separado:

```bash
STACK_NAME=sri-xades-signer-cliente1 \
SECRET_NAME=sri-xades-signer/cliente1 \
STATE_FILE=target/aws-test-env-cliente1 \
./scripts/aws-create-test-env.sh \
  --p12 /ruta/cliente1.p12 \
  --alias cliente1
```

Para eliminarlo, usa el mismo `STATE_FILE`:

```bash
STATE_FILE=target/aws-test-env-cliente1 \
./scripts/aws-delete-test-env.sh
```

El nombre de la función Lambda ahora es el nombre del stack, así que puedes tener varios
stacks con el mismo `Stage` sin que choquen.

Opciones útiles:

```bash
./scripts/aws-create-test-env.sh --rebuild          # compila con Java 21 en vez de descargar
./scripts/aws-create-test-env.sh --zip ruta.zip     # usa un ZIP local
```

### Eliminar

```bash
./scripts/aws-delete-test-env.sh
```

Pide escribir `ELIMINAR`. Para automatización:

```bash
./scripts/aws-delete-test-env.sh --yes
```

Elimina el stack (Lambda, API Gateway, rol, log group) y el secreto. **No** elimina el
usuario IAM, sus access keys ni la cuota de concurrencia de la cuenta. El borrado del
secreto es irreversible.

### Archivos locales

- `target/aws-test-env`: stack, región, ARN y endpoint; no contiene la clave privada.
- `target/test-cert.p12`: certificado autofirmado de prueba.
- `target/test-cert-password.txt`: contraseña del certificado de prueba.
- `target/test-cert-key.pem` y `target/test-cert.pem`: material del certificado de prueba.

Todo `target/` está ignorado por Git.
