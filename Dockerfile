# Imagen opcional de AWS Lambda. El flujo principal usa target/lambda.zip.
#
# Construir después de `mvn -B verify`:
#   docker build -t sri-xades-signer .
#
# Probar localmente:
#   docker run --rm -p 9000:8080 \
#     -e CERTIFICATE_SECRET_ID=... \
#     -e AWS_REGION=us-east-1 \
#     sri-xades-signer
#
# La imagen requiere credenciales AWS para leer Secrets Manager. No baked-in secrets.
FROM public.ecr.aws/lambda/java:21

ARG JAR_FILE=target/sri-xades-signer-0.1.1.jar
COPY ${JAR_FILE} ${LAMBDA_TASK_ROOT}/lib/

CMD ["com.julioserpone.srixadessigner.lambda.SignHandler::handleRequest"]
