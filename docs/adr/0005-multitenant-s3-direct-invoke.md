# ADR 0005: Multicliente con S3 e invocación directa de Lambda

- Estado: Aceptado
- Fecha: 2026-10-20

## Contexto

El modo original usa un único PKCS#12 en Secrets Manager y expone API Gateway. Para atender a unos
100 clientes con su propio certificado, el backend Laravel necesita elegir el certificado por
cliente sin crear una Lambda, un secreto o un usuario IAM por cliente. El costo y la operación
deben mantenerse bajos.

## Decisión

Agregar un modo multicliente:

- Bucket S3 privado con SSE-S3 y versionado.
- Una clave por inquilino: `tenants/<uuid>/certificate.p12`.
- Una sola Lambda compartida, invocada directamente con IAM (`RequestResponse`).
- El backend envía `tenantUuid`, `password` y `xmlBase64`; recibe `signedXmlBase64`.
- El UUID se valida estrictamente y no se aceptan rutas S3 arbitrarias.
- La identidad se cachea por inquilino; el password se verifica con SHA-256 en cada invocación.
- El password se usa solo para abrir el PKCS#12 y se limpia de memoria; no se registra.

El modo legacy con Secrets Manager y API Gateway se mantiene para no romper el entorno actual.

## Alternativas consideradas

- **Una Lambda por cliente:** no ahorra costo en reposo y multiplica configuración, roles y
  despliegues.
- **Un secreto de Secrets Manager por cliente:** costo mensual por secreto y más operación.
- **Un usuario IAM por cliente:** no aporta aislamiento real; el backend es el único que invoca.
- **CMK de KMS:** una sola CMK para todos los clientes costaría ~USD 1/mes, pero no es necesaria
  para el MVP; SSE-S3 es suficiente y gratuita.
- **DynamoDB:** útil para metadatos, pero no reemplaza el almacenamiento del PKCS#12 ni es
  necesario para el MVP.

## Consecuencias

- El backend debe proteger el password y enviarlo solo por TLS/IAM.
- La Lambda puede leer cualquier objeto bajo `tenants/`; el aislamiento se controla por el UUID y
  el password que envía el backend. No se debe permitir que un inquilino invoque la Lambda.
- La rotación de certificado se hace sobrescribiendo la clave S3; el caché puede tardar hasta el
  TTL configurado.
- El bucket debe conservarse y auditarse; la plantilla usa `DeletionPolicy: Retain`.
- Sigue siendo un modo experimental hasta la validación en el ambiente de certificación del SRI.
