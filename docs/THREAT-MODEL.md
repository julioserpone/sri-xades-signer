# Modelo de amenazas

Este documento resume las amenazas principales del firmador y las mitigaciones implementadas o
pendientes. No sustituye una revisión de seguridad formal.

## Activos

- Clave privada RSA y certificado PKCS#12.
- Contraseña del PKCS#12.
- Capacidad de producir firmas válidas.
- XML de comprobantes y datos tributarios.
- Credenciales IAM y configuración AWS.
- Reputación/cumplimiento del emisor.

## Fronteras de confianza

```text
Modo legacy:
Cliente autorizado --SigV4--> API Gateway IAM --> Lambda --> Secrets Manager

Modo multicliente:
Backend Laravel --IAM/SDK--> Lambda --> S3 privado (tenants/<uuid>/certificate.p12)
                                      |
                                      +--> DSS / BouncyCastle
```

- El cliente no es confiable más allá de su identidad IAM.
- El XML es no confiable.
- En el modo legacy, Secrets Manager y el rol IAM son la frontera de la clave privada.
- En el modo multicliente, el bucket S3 privado y el rol IAM de la Lambda son la frontera de la
  clave privada; el backend solo debe poder invocar la función.
- CloudWatch no debe recibir secretos ni XML.

## Amenazas y mitigaciones

| Amenaza | Impacto | Mitigación | Estado |
|---|---|---|---|
| Endpoint público actúa como oráculo de firma | Alto | API Gateway `AWS_IAM`, sin API keys ni rutas públicas | Implementado |
| Invocación directa sin IAM | Alto | La función no tiene endpoint público; el rol del backend es el único autorizado | Implementado |
| Lectura de certificados de otro inquilino | Crítico | Bucket privado, `s3:GetObject` solo para la Lambda, UUID validado y prefijo fijo | Implementado |
| Password incorrecto usando identidad cacheada | Alto | Hash SHA-256 del password verificado en cada invocación | Implementado |
| Fuga del password en logs o errores | Crítico | No se registra; errores genéricos; `char[]` se limpia | Implementado |
| Robo de clave privada del repositorio | Crítico | `.gitignore`, revisión, secret scanning; PKCS#12 solo en Secrets Manager o S3 privado | Implementado |
| Clave en variable de entorno o logs | Crítico | El handler solo recibe el ARN o lee S3; no registra secretos | Implementado |
| XXE / lectura de archivos | Alto | Parser sin DTD, entidades externas, XInclude ni schemas externos | Implementado |
| Expansión de entidades / DoS | Medio | `FEATURE_SECURE_PROCESSING`, límite de bytes, throttling y concurrencia reservada opcional | Implementado |
| Firma de contenido arbitrario | Alto | Lista de raíces SRI, `id` obligatorio, un XML por llamada | Implementado |
| Doble firma / confusión | Medio | Rechazo si ya existe `ds:Signature` | Implementado |
| Firma con certificado vencido | Medio | `checkValidity()` configurable | Implementado |
| Manipulación del XML después de firmar | Alto | Firma enveloped + verificación XMLDSig posterior | Implementado |
| Fuga de XML en errores/logs | Alto | Errores genéricos; logs solo con `requestId` y clase | Implementado |
| Abuso de costo | Medio | Concurrencia reservada, throttling, tamaño máximo | Implementado |
| Repudio / falta de evidencia | Medio | `SigningTime`, logs de `requestId`, hash del XML | Parcial |
| Compromiso de credenciales IAM | Alto | Mínimo privilegio, rotación, MFA, revisión de accesos | Operativo |
| Compromiso de Secrets Manager | Crítico | CMK opcional, rotación, auditoría CloudTrail | Operativo |
| Dependencia vulnerable | Alto | Dependabot, CodeQL, `mvn verify`, revisión | Parcial |
| Algoritmo SHA-1 débil | Medio | Requisito SRI; no usar para otros fines | Aceptado |
| Falta de certificación SRI | Alto | No usar en producción; checklist | Pendiente |

## Controles operativos recomendados

- Usar una cuenta AWS dedicada o un rol con alcance mínimo.
- Habilitar CloudTrail y alertas sobre `GetSecretValue` y `GetObject` anómalos.
- Restringir quién puede modificar el secreto, el bucket de certificados y la función.
- Mantener el bucket S3 con bloqueo de acceso público, SSE-S3 y versionado.
- No dar `s3:GetObject` al backend; solo la Lambda debe leer los PKCS#12.
- Usar una CMK de KMS si la política de la organización lo exige.
- Configurar WAF y límites de tasa si el endpoint se expone a muchos clientes.
- Revisar logs sin incluir cuerpos XML.
- Rotar credenciales y certificados.
- Probar restauración y reversión.
- Ejecutar `mvn -B verify` y revisar Dependabot antes de cada release.

## Riesgos residuales

- El endpoint o la invocación directa siguen siendo una capacidad de firma para cualquier
  identidad IAM autorizada; la autorización granular depende de la política IAM.
- En el modo multicliente, el backend puede enviar el UUID y el password de cualquier inquilino.
  El aislamiento entre clientes depende de la lógica de autorización del backend, no de la Lambda.
- SHA-1 es obligatorio por la ficha y no debe reutilizarse en otros sistemas.
- La aceptación del SRI no está garantizada hasta la certificación.
- La disponibilidad depende de AWS, de Secrets Manager o de S3 según el modo.
