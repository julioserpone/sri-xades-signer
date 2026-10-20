# Contribuir

Gracias por contribuir a `sri-xades-signer`. Este proyecto firma documentos tributarios y maneja
material criptográfico, así que la revisión es deliberadamente conservadora.

## Antes de empezar

- Busca un issue existente o abre uno para discutir cambios grandes.
- No publiques certificados reales, contraseñas, XML de contribuyentes ni secretos.
- Para vulnerabilidades, sigue [SECURITY.md](SECURITY.md); no abras un issue público.
- Respeta el [Código de Conducta](CODE_OF_CONDUCT.md).

## Flujo de ramas

`main` está protegida. No se permite push directo.

1. Crea una rama desde `main`:
   - `feat/descripcion-corta`
   - `fix/descripcion-corta`
   - `docs/descripcion-corta`
   - `chore/descripcion-corta`
2. Haz cambios pequeños y enfocados.
3. Ejecuta las verificaciones locales.
4. Abre un pull request usando la plantilla.
5. Espera el check obligatorio `ci-success` y la revisión del CODEOWNER.

Usamos squash merge e historial lineal. El título del PR debe seguir Conventional Commits
(`feat:`, `fix:`, `docs:`, `test:`, `build:`, `ci:`, `chore:`, `refactor:`).

## Verificación local

```bash
export JAVA_HOME=/ruta/a/jdk-21
mvn -B spotless:apply
mvn -B verify
./scripts/package-lambda.sh --skip-build
```

Si cambias el empaquetado o la plantilla SAM, valida también:

```bash
unzip -l target/lambda.zip
sam validate --template-file infra/template.yaml
```

## Reglas de código

- Java 21, Google Java Format, 2 espacios.
- Código e identificadores en inglés; documentación de usuario en español.
- No agregues dependencias sin justificar tamaño, licencia y mantenimiento.
- No uses `System.out` para logs de producción; usa el logger del contexto Lambda.
- No registres XML, secretos, contraseñas ni material de clave.
- Mantén el parser XML endurecido y los límites de entrada.
- Agrega pruebas para comportamiento nuevo y para cada corrección de seguridad.

## Cambios de firma

Cualquier cambio en el perfil XAdES-BES requiere:

- Un ADR en `docs/adr/`.
- Pruebas que validen la estructura y la firma criptográfica.
- Evidencia de validación en el ambiente de certificación del SRI si se pretende producción.
- Actualización de `README.md`, `docs/API.md` y `CHANGELOG.md`.

No se aceptan cambios que:

- Sustituyan RSA-SHA1/SHA-1 por otro algoritmo sin respaldo normativo.
- Eliminen `SigningCertificate` v1, `SignedProperties` o la firma de `KeyInfo`.
- Conviertan el endpoint en un oráculo de firma público.
- Lean la clave privada desde variables de entorno o desde el repositorio.
- Debiliten el parser, el límite de tamaño o la lista de raíces.

## Pull requests

Incluye:

- Qué cambia y por qué.
- Riesgo y plan de reversión.
- Pruebas ejecutadas.
- Impacto en seguridad, costo o contrato HTTP.
- Capturas o XML de ejemplo **anonimizados** si son útiles.

El PR debe tener `ci-success` en verde antes de fusionarse. No se permite bypass del ruleset.

## Releases

1. Actualiza `CHANGELOG.md` y la versión en `pom.xml`.
2. Ejecuta `mvn -B verify`.
3. Genera `target/lambda.zip` y su SHA-256.
4. Crea un tag `vX.Y.Z`.
5. El workflow de release adjunta el ZIP al GitHub Release.
6. No anuncies certificación SRI ni producción sin evidencia.

## Licencia

Al contribuir aceptas que tu aporte se distribuya bajo [Apache-2.0](LICENSE).
