# ADR 0003: Clave privada en AWS Secrets Manager

- Estado: Aceptado
- Fecha: 2026-10-20

## Contexto

El firmador necesita un PKCS#12 y su contraseña en tiempo de ejecución. Guardarlos en el
repositorio, en una imagen Docker, en variables de entorno o en el cuerpo HTTP aumenta el riesgo
de filtración. El servicio debe poder rotar el certificado sin redeploy.

## Decisión

Guardar el PKCS#12 en base64 y la contraseña en **AWS Secrets Manager** como JSON:

```json
{"p12Base64":"...","password":"...","alias":"opcional"}
```

La Lambda recibe solo `CERTIFICATE_SECRET_ID` (ARN o nombre), lo lee con el rol IAM y cachea el
material en memoria durante `CERTIFICATE_CACHE_SECONDS` (300 s por defecto). No se registra el
secreto.

La política IAM permite `secretsmanager:GetSecretValue` solo sobre el ARN configurado. Si se usa
una CMK, se agrega `kms:Decrypt` sobre esa clave.

## Alternativas consideradas

- **Variable de entorno:** visible en la configuración de Lambda, logs de despliegue y consola.
- **S3:** requiere cifrado, políticas y rotación adicional; Secrets Manager ya ofrece auditoría.
- **KMS directo:** habría que cifrar/descifrar el PKCS#12 y gestionar la contraseña; más complejo.
- **Certificado en el JAR:** imposible de rotar y se filtra con el artefacto.

## Consecuencias

- Dependencia del SDK de AWS y de Secrets Manager.
- La rotación consiste en actualizar el secreto; no requiere nuevo deploy.
- El caché puede servir el certificado anterior hasta el TTL; se documenta.
- El rol debe tener permiso mínimo y el secreto debe estar auditado.
