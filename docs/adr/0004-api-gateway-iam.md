# ADR 0004: API Gateway con IAM y XML crudo

- Estado: Aceptado
- Fecha: 2026-10-20

## Contexto

Un firmador es un oráculo de firma: cualquiera que pueda invocarlo puede producir documentos
firmados con la clave del emisor. El cliente PHP existente podría integrarse por HTTP. También se
quiere evitar JSON con XML escapado y facilitar la validación.

## Decisión

Exponer `POST /v1/sign` en API Gateway con autorización **AWS_IAM**. El cuerpo es el XML crudo
(`application/xml`) y la respuesta es el XML firmado. No hay API keys ni endpoint público.

El cliente firma con SigV4 y usa credenciales IAM con `execute-api:Invoke`. El handler acepta body
directo o base64 y responde JSON solo en errores.

## Alternativas consideradas

- **API key:** no identifica de forma fuerte y se filtra con facilidad.
- **Cognito:** útil para usuarios finales, pero el cliente es un backend; IAM es más simple.
- **JSON `{xml: "..."}`:** añade escape, tamaño y riesgo de doble codificación.
- **Lambda Function URL pública:** requiere IAM o se vuelve un oráculo; API Gateway ofrece
  throttling y control de ruta.

## Consecuencias

- El cliente debe implementar SigV4 o usar el SDK de AWS.
- API Gateway añade costo y latencia.
- Se puede agregar WAF, usage plans y autorización granular por identidad.
- El contrato es simple de consumir desde PHP con un cliente HTTP que firme SigV4.
