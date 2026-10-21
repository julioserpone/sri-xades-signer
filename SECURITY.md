# Política de seguridad

## Estado

Este proyecto es **experimental** y no está certificado por el SRI. No se debe usar con
comprobantes electrónicos reales ni declarar producción hasta completar la validación en el
ambiente de certificación.

## Reportar una vulnerabilidad

**No abras un issue público.** Usa el reporte privado de vulnerabilidades de GitHub:

1. Ve a la pestaña **Security** del repositorio.
2. Selecciona **Advisories** → **Report a vulnerability**.
3. Incluye:
   - Versión o commit afectado.
   - Descripción del impacto.
   - Pasos de reproducción mínimos y seguros.
   - Si aplica, un parche sugerido.

No envíes claves privadas, certificados reales, contraseñas ni datos de contribuyentes. Si el
reporte requiere material sensible, coordina primero un canal seguro.

## Alcance

Se consideran vulnerabilidades:

- Exposición de la clave privada, del PKCS#12 o de la contraseña.
- Endpoint accesible sin autenticación IAM o bypass de autorización.
- Firma de contenido arbitrario o doble firma.
- XXE, expansión de entidades, SSRF o lectura de archivos vía XML.
- Ejecución remota de código, inyección o deserialización insegura.
- Fuga de XML, secretos o datos personales en logs o respuestas.
- Debilitamiento del perfil XAdES-BES requerido.
- Dependencia con vulnerabilidad crítica explotable en el flujo de firma.

Quedan fuera de alcance, salvo que demuestren impacto real:

- Falta de cabeceras de seguridad en documentación estática.
- Ingeniería social.
- Ataques de denegación de servicio sin límites de costo.
- Reportes automáticos de escáner sin reproducción.

## Modelo de seguridad

- **Autenticación:** API Gateway con `AWS_IAM` (modo legacy) o invocación directa de Lambda con
  IAM (modo multicliente). El cliente firma con SigV4 o usa el SDK de AWS.
- **Autorización:** rol IAM con `secretsmanager:GetSecretValue` sobre un ARN concreto o
  `s3:GetObject` sobre el prefijo `tenants/` del bucket de certificados.
- **Clave privada:** PKCS#12 almacenado en Secrets Manager (legacy) o en un bucket S3 privado
  (multicliente); no se acepta por HTTP ni por entorno.
- **Password:** en el modo multicliente viaja en la invocación IAM/TLS, se usa solo para abrir el
  PKCS#12 y se limpia de memoria; no se persiste en AWS ni se registra.
- **Aislamiento:** una función Lambda, sin acceso a red salvo AWS APIs; no se necesita VPC.
- **Entrada:** XML UTF-8, tamaño máximo configurable, raíz permitida y sin firma previa.
- **Parser:** DTD, entidades externas, XInclude y esquemas externos deshabilitados.
- **Salida:** XML firmado; no se registra el cuerpo ni el secreto.
- **Costo/abuso:** concurrencia reservada y throttling en API Gateway o concurrencia reservada en
  la invocación directa.

## Rotación de certificado

### Modo legacy (Secrets Manager)

1. Crea el nuevo PKCS#12 y valida que la clave coincida con el certificado.
2. Actualiza el secreto en Secrets Manager.
3. Espera `CERTIFICATE_CACHE_SECONDS` o fuerza un cold start.
4. Firma un comprobante de prueba y valida la cadena y la firma.
5. Revoca el certificado anterior según el procedimiento de tu entidad certificadora.

### Modo multicliente (S3)

1. Crea el nuevo PKCS#12 y valida que la clave coincida con el certificado.
2. Sobrescribe `tenants/<uuid>/certificate.p12` en el bucket privado.
3. Espera `CERTIFICATE_CACHE_SECONDS` o fuerza un cold start.
4. Firma un comprobante de prueba y valida la cadena y la firma.
5. Revoca el certificado anterior según el procedimiento de tu entidad certificadora.

No guardes el PKCS#12 anterior en el repositorio ni en imágenes Docker.

## Dependencias

- Dependabot revisa Maven y GitHub Actions.
- `mvn verify` ejecuta pruebas y SpotBugs.
- Revisa los avisos de seguridad de GitHub y las licencias de terceros.
- No actualices DSS sin ejecutar las pruebas de perfil y validar en certificación.

## Divulgación

Coordinaremos la corrección y la divulgación responsable. No hay programa de recompensas.
