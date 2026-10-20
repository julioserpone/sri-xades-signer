# CLAUDE.md

Lee y sigue [`AGENTS.md`](AGENTS.md). Es la fuente de verdad para el perfil XAdES-BES, la
seguridad y el flujo de cambios.

Notas específicas para Claude:

- Usa un plan breve antes de cambios que toquen `XadesBesSigner`, `SignHandler`, PKCS#12,
  Secrets Manager o la plantilla SAM.
- No cambies `SignatureLevel.XAdES_BASELINE_B` + `setEn319132(false)` sin entender por qué se
  usa: DSS 6.5.1 no completa el flujo de extensión con `XAdES_BES`, pero `en319132=false`
  produce los elementos XAdES 1.3.2 y `SigningCertificate` v1 que requiere la ficha.
- Mantén la referencia explícita al `id` de la raíz con transformada `enveloped-signature`.
- Después de editar, ejecuta `mvn -B spotless:apply` y `mvn -B verify`.
- No declares certificación SRI ni producción sin evidencia.
- No incluyas certificados, contraseñas, XML reales ni secretos en pruebas o commits.
