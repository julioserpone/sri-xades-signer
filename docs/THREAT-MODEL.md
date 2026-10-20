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
Cliente autorizado --SigV4--> API Gateway IAM --> Lambda --> Secrets Manager
                                                   |
                                                   +--> DSS / BouncyCastle
```

- El cliente no es confiable más allá de su identidad IAM.
- El XML es no confiable.
- Secrets Manager y el rol IAM son la frontera de la clave privada.
- CloudWatch no debe recibir secretos ni XML.

## Amenazas y mitigaciones

| Amenaza | Impacto | Mitigación | Estado |
|---|---|---|---|
| Endpoint público actúa como oráculo de firma | Alto | API Gateway `AWS_IAM`, sin API keys ni rutas públicas | Implementado |
| Robo de clave privada del repositorio | Crítico | `.gitignore`, revisión, secret scanning; PKCS#12 solo en Secrets Manager | Implementado |
| Clave en variable de entorno o logs | Crítico | El handler solo recibe el ARN; no registra secretos | Implementado |
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
- Habilitar CloudTrail y alertas sobre `GetSecretValue` anómalo.
- Restringir quién puede modificar el secreto y la función.
- Usar una CMK de KMS si la política de la organización lo exige.
- Configurar WAF y límites de tasa si el endpoint se expone a muchos clientes.
- Revisar logs sin incluir cuerpos XML.
- Rotar credenciales y certificados.
- Probar restauración y reversión.
- Ejecutar `mvn -B verify` y revisar Dependabot antes de cada release.

## Riesgos residuales

- El endpoint sigue siendo una capacidad de firma para cualquier identidad IAM autorizada; la
  autorización granular depende de la política de API Gateway/IAM.
- SHA-1 es obligatorio por la ficha y no debe reutilizarse en otros sistemas.
- La aceptación del SRI no está garantizada hasta la certificación.
- La disponibilidad depende de AWS y de Secrets Manager.
