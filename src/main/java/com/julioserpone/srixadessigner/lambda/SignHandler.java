package com.julioserpone.srixadessigner.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.julioserpone.srixadessigner.InvalidXmlException;
import com.julioserpone.srixadessigner.PayloadTooLargeException;
import com.julioserpone.srixadessigner.SigningException;
import com.julioserpone.srixadessigner.XadesBesSigner;
import com.julioserpone.srixadessigner.config.SignerConfig;
import com.julioserpone.srixadessigner.crypto.CertificateLoadException;
import com.julioserpone.srixadessigner.crypto.SecretsManagerSigningIdentityProvider;
import com.julioserpone.srixadessigner.crypto.SigningIdentityProvider;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * AWS Lambda handler for {@code POST /v1/sign}.
 *
 * <p>The endpoint must be protected by API Gateway IAM authentication. The handler accepts a single
 * XML document and returns the same document with an enveloped XAdES-BES signature appended.
 */
public final class SignHandler
    implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

  private static final Logger LOGGER = Logger.getLogger(SignHandler.class.getName());
  private static final String XML_CONTENT_TYPE = "application/xml; charset=utf-8";

  private final SignerConfig config;
  private final XadesBesSigner signer;
  private final SigningIdentityProvider identityProvider;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public SignHandler() {
    this(SignerConfig.fromEnvironment(), SecretsManagerSigningIdentityProvider.fromEnvironment());
  }

  SignHandler(SignerConfig config, SigningIdentityProvider identityProvider) {
    this.config = config;
    this.signer = new XadesBesSigner(config);
    this.identityProvider = identityProvider;
  }

  @Override
  public APIGatewayProxyResponseEvent handleRequest(
      APIGatewayProxyRequestEvent input, Context context) {
    String requestId = requestId(input, context);

    try {
      String method = input.getHttpMethod();
      if (method == null && input.getRequestContext() != null) {
        method = input.getRequestContext().getHttpMethod();
      }
      if (!"POST".equalsIgnoreCase(method)) {
        return errorResponse(405, "method_not_allowed", requestId);
      }
      if (!hasXmlContentType(input.getHeaders())) {
        return errorResponse(415, "unsupported_media_type", requestId);
      }

      byte[] xml = requestBody(input);
      byte[] signed = signer.sign(xml, identityProvider.get());
      return xmlResponse(200, signed, requestId);
    } catch (PayloadTooLargeException e) {
      return errorResponse(413, "payload_too_large", requestId);
    } catch (InvalidXmlException | IllegalArgumentException e) {
      return errorResponse(400, "invalid_xml", requestId);
    } catch (CertificateLoadException e) {
      logError(context, requestId, e);
      return errorResponse(500, "certificate_unavailable", requestId);
    } catch (SigningException e) {
      logError(context, requestId, e);
      return errorResponse(500, "signing_failed", requestId);
    } catch (RuntimeException e) {
      logError(context, requestId, e);
      return errorResponse(500, "internal_error", requestId);
    }
  }

  private byte[] requestBody(APIGatewayProxyRequestEvent input) {
    String body = input.getBody();
    if (body == null || body.isBlank()) {
      throw new InvalidXmlException("The request body is empty");
    }
    if (!Boolean.TRUE.equals(input.getIsBase64Encoded())) {
      return body.getBytes(StandardCharsets.UTF_8);
    }
    try {
      return Base64.getDecoder().decode(body);
    } catch (IllegalArgumentException e) {
      throw new InvalidXmlException("The request body is not valid base64", e);
    }
  }

  private APIGatewayProxyResponseEvent xmlResponse(int status, byte[] xml, String requestId) {
    return new APIGatewayProxyResponseEvent()
        .withStatusCode(status)
        .withHeaders(
            Map.of(
                "Content-Type",
                XML_CONTENT_TYPE,
                "Cache-Control",
                "no-store",
                "X-Content-Type-Options",
                "nosniff",
                "X-Request-Id",
                requestId))
        .withBody(new String(xml, StandardCharsets.UTF_8))
        .withIsBase64Encoded(false);
  }

  private APIGatewayProxyResponseEvent errorResponse(int status, String code, String requestId) {
    try {
      String body = objectMapper.writeValueAsString(Map.of("error", code, "requestId", requestId));
      return new APIGatewayProxyResponseEvent()
          .withStatusCode(status)
          .withHeaders(
              Map.of(
                  "Content-Type", "application/json; charset=utf-8",
                  "Cache-Control", "no-store",
                  "X-Content-Type-Options", "nosniff",
                  "X-Request-Id", requestId))
          .withBody(body)
          .withIsBase64Encoded(false);
    } catch (Exception e) {
      return new APIGatewayProxyResponseEvent()
          .withStatusCode(500)
          .withBody("{\"error\":\"internal_error\"}")
          .withIsBase64Encoded(false);
    }
  }

  private static boolean hasXmlContentType(Map<String, String> headers) {
    if (headers == null) {
      return false;
    }
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      if ("content-type".equals(entry.getKey().toLowerCase(Locale.ROOT))) {
        String value = entry.getValue();
        if (value == null) {
          return false;
        }
        String mediaType = value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return "application/xml".equals(mediaType) || "text/xml".equals(mediaType);
      }
    }
    return false;
  }

  private static String requestId(APIGatewayProxyRequestEvent input, Context context) {
    if (input != null
        && input.getRequestContext() != null
        && input.getRequestContext().getRequestId() != null) {
      return input.getRequestContext().getRequestId();
    }
    if (context != null && context.getAwsRequestId() != null) {
      return context.getAwsRequestId();
    }
    return UUID.randomUUID().toString();
  }

  private static void logError(Context context, String requestId, Throwable error) {
    String message =
        "requestId=" + requestId + (error == null ? "" : " error=" + error.getClass().getName());
    if (context != null && context.getLogger() != null) {
      context.getLogger().log(message);
    } else {
      LOGGER.warning(message);
    }
  }
}
