# Certificación SRI

## Estado

**Pendiente.** El firmador no ha sido validado en el ambiente de certificación del SRI. El
proyecto es experimental y no debe usarse con comprobantes reales ni declararse en producción.

Este documento define la evidencia mínima que falta y evita afirmaciones prematuras.

## Perfil implementado

| Requisito | Implementación |
|---|---|
| XAdES-BES enveloped | `XAdES_BASELINE_B` de DSS con `setEn319132(false)` y referencia enveloped explícita |
| Namespace XAdES | `http://uri.etsi.org/01903/v1.3.2#` |
| Firma | RSA-SHA1 (`http://www.w3.org/2000/09/xmldsig#rsa-sha1`) |
| Digest | SHA-1 en documento, `SignedProperties` y `KeyInfo` |
| Certificado | `SigningCertificate` v1 con `CertDigest` SHA-1 e `IssuerSerial` |
| `KeyInfo` | Incluido y firmado |
| Codificación | UTF-8 |
| Referencia al comprobante | `URI="#<id-raíz>"` + `enveloped-signature` |

La ficha SRI y el Anexo 14 muestran una firma de referencia con `SigningCertificate` v1,
`SignedProperties`, `KeyInfo` y referencia al `id="comprobante"`. El proyecto sigue esa estructura,
pero **la aceptación final depende del validador del SRI**.

## Matriz de pruebas pendiente

| Caso | Evidencia esperada | Estado |
|---|---|---|
| XML de factura firmado y recibido por el SRI | Respuesta de recepción/autorización del ambiente de certificación | Pendiente |
| Firma validada por herramienta del SRI | Captura o log de validación | Pendiente |
| Cadena de certificados aceptada | Validación de certificado y cadena | Pendiente |
| Documento alterado rechazado | Error de firma/no autorizado | Pendiente |
| Certificado vencido rechazado | Error controlado | Pendiente |
| XML con DTD/XXE rechazado | `400 invalid_xml` | Cubierto por pruebas locales |
| Documento ya firmado rechazado | `400 invalid_xml` | Cubierto por pruebas locales |
| Payload sobredimensionado | `413` | Cubierto por pruebas locales |
| Método/contenido incorrecto | `405`/`415` | Cubierto por pruebas locales |
| Endpoint sin IAM | `403` de API Gateway | Pendiente de prueba en AWS |
| Rotación de certificado | Firma válida con el nuevo certificado | Pendiente |

## Procedimiento sugerido

1. Solicita acceso al ambiente de certificación del SRI y confirma la versión vigente de la ficha.
2. Genera un comprobante de prueba con datos ficticios y un certificado de pruebas aceptado por
   el ambiente.
3. Firma con este Lambda o con el CLI local.
4. Valida el XML con la herramienta oficial del SRI y con una verificación XMLDSig independiente.
5. Envía el comprobante al servicio de recepción del SRI en certificación.
6. Registra:
   - Fecha, versión del código (`git describe --tags`), versión de DSS.
   - Huella SHA-256 del XML firmado y del PKCS#12 (sin exponer la clave).
   - `requestId` y resultado del SRI.
   - Errores y observaciones del validador.
7. Si el SRI rechaza la firma, conserva el XML y el mensaje, abre un issue con datos anonimizados
   y no cambies el perfil sin un ADR.

## Criterios de aceptación

Se considerará “listo para certificación” cuando:

- `mvn -B verify` esté en verde.
- Exista un XML firmado aceptado por el SRI en certificación.
- La firma se valide criptográficamente con el certificado público.
- El SRI no reporte errores de estructura XAdES, algoritmo, certificado o referencia.
- Exista un procedimiento documentado de rotación, monitoreo y reversión.

Se considerará “listo para producción” solo después de:

- Aceptación explícita del SRI en certificación.
- Revisión de seguridad del despliegue AWS.
- Pruebas de carga y límites de costo.
- Aprobación del responsable del proyecto y del CODEOWNER.

## Riesgos conocidos

- **DSS Baseline-B:** DSS 6.5.1 no completa el flujo de extensión con `SignatureLevel.XAdES_BES`.
  Se usa `XADES_BASELINE_B` con `setEn319132(false)` para obtener los elementos XAdES 1.3.2 y
  `SigningCertificate` v1. El resultado debe validarse contra el SRI.
- **SHA-1:** es obligatorio por la ficha, aunque sea un algoritmo legacy. No lo cambies sin
  respaldo normativo.
- **Sin sello de tiempo:** el perfil BES no incluye `SignatureTimeStamp`; no se implementa TSA.
- **Sin revocación en línea:** no se consulta OCSP/CRL al firmar. La validación de revocación es
  responsabilidad del receptor/proceso de negocio.
- **Un certificado por despliegue:** el secreto contiene un PKCS#12; la rotación es operativa.

## Declaración

Mientras este documento diga **Pendiente**, cualquier afirmación de “certificado”, “producción”,
“oficial” o “avalado por el SRI” es incorrecta.
