## Resumen

<!-- Qué cambia y por qué. -->

## Tipo de cambio

- [ ] Corrección de error
- [ ] Nueva funcionalidad
- [ ] Cambio de seguridad
- [ ] Documentación
- [ ] Refactor / mantenimiento
- [ ] Cambio de perfil XAdES o del contrato API

## Riesgo y reversión

<!-- Impacto en seguridad, costo, compatibilidad o certificación. ¿Cómo se revierte? -->

## Pruebas

<!-- Comandos ejecutados y resultados. No incluyas XML reales, certificados ni secretos. -->

- [ ] `mvn -B verify`
- [ ] `./scripts/package-lambda.sh --skip-build`
- [ ] Prueba manual del endpoint (si aplica)
- [ ] Validación en ambiente de certificación SRI (si aplica)

## Seguridad

- [ ] No se agregaron certificados, contraseñas, secretos ni XML reales.
- [ ] No se agregó una ruta pública sin IAM.
- [ ] No se debilitó el parser XML, el límite de tamaño ni la lista de raíces.
- [ ] No se cambió el perfil de firma sin ADR y pruebas.
- [ ] Los logs no exponen XML, secretos ni material de clave.

## Checklist

- [ ] Actualicé `CHANGELOG.md` si corresponde.
- [ ] Actualicé la documentación/ADR si cambió el contrato o la seguridad.
- [ ] El check `ci-success` está en verde.
- [ ] No se declaró certificación/producción sin evidencia.
