package com.julioserpone.srixadessigner.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.julioserpone.srixadessigner.InvalidXmlException;
import com.julioserpone.srixadessigner.PayloadTooLargeException;
import com.julioserpone.srixadessigner.SigningException;
import com.julioserpone.srixadessigner.XadesBesSigner;
import com.julioserpone.srixadessigner.config.SignerConfig;
import com.julioserpone.srixadessigner.crypto.CertificateLoadException;
import com.julioserpone.srixadessigner.crypto.S3SigningIdentityProvider;
import com.julioserpone.srixadessigner.crypto.SigningIdentity;
import com.julioserpone.srixadessigner.crypto.TenantSigningIdentityProvider;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * AWS Lambda handler for direct invocation with {@code RequestResponse}.
 *
 * <p>Request JSON:
 *
 * <pre>{@code
 * {"tenantUuid":"...","password":"...","xmlBase64":"..."}
 * }</pre>
 *
 * <p>Success response JSON:
 *
 * <pre>{@code
 * {"signedXmlBase64":"...","requestId":"..."}
 * }</pre>
 *
 * <p>Errors are returned as {@code {"error":"code","requestId":"..."}} so the caller can map them
 * without exposing internals. The password and XML are never logged.
 */
public final class InvokeSignHandler
    implements RequestHandler<Map<String, Object>, Map<String, Object>> {

  private static final Logger LOGGER = Logger.getLogger(InvokeSignHandler.class.getName());

  private final SignerConfig config;
  private final XadesBesSigner signer;
  private final TenantSigningIdentityProvider identityProvider;

  public InvokeSignHandler() {
    this(SignerConfig.fromEnvironment(), S3SigningIdentityProvider.fromEnvironment());
  }

  InvokeSignHandler(SignerConfig config, TenantSigningIdentityProvider identityProvider) {
    this.config = config;
    this.signer = new XadesBesSigner(config);
    this.identityProvider = identityProvider;
  }

  @Override
  public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {
    String requestId = requestId(context);

    try {
      String tenantUuid = requiredString(input, "tenantUuid");
      String password = requiredNonEmptyString(input, "password");
      String xmlBase64 = requiredString(input, "xmlBase64");

      if ((long) xmlBase64.length() > ((long) config.maxXmlBytes() * 4 / 3) + 8) {
        throw new PayloadTooLargeException("The encoded XML payload is too large");
      }

      byte[] xml;
      try {
        xml = Base64.getDecoder().decode(xmlBase64);
      } catch (IllegalArgumentException e) {
        throw new InvalidXmlException("The XML payload is not valid base64", e);
      }

      char[] passwordChars = password.toCharArray();
      try {
        SigningIdentity identity = identityProvider.get(tenantUuid, passwordChars);
        byte[] signed = signer.sign(xml, identity);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("signedXmlBase64", Base64.getEncoder().encodeToString(signed));
        response.put("requestId", requestId);
        return response;
      } finally {
        Arrays.fill(passwordChars, '\0');
      }
    } catch (PayloadTooLargeException e) {
      return errorResponse("payload_too_large", requestId);
    } catch (InvalidXmlException | IllegalArgumentException e) {
      return errorResponse("invalid_request", requestId);
    } catch (CertificateLoadException e) {
      logError(context, requestId, e);
      return errorResponse("certificate_unavailable", requestId);
    } catch (SigningException e) {
      logError(context, requestId, e);
      return errorResponse("signing_failed", requestId);
    } catch (RuntimeException e) {
      logError(context, requestId, e);
      return errorResponse("internal_error", requestId);
    }
  }

  private static String requiredString(Map<String, Object> input, String name) {
    String value = requiredNonEmptyString(input, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException("The request field " + name + " is required");
    }
    return value;
  }

  private static String requiredNonEmptyString(Map<String, Object> input, String name) {
    if (input == null) {
      throw new IllegalArgumentException("The request is empty");
    }
    Object value = input.get(name);
    if (!(value instanceof String text) || text.isEmpty()) {
      throw new IllegalArgumentException("The request field " + name + " is required");
    }
    return text;
  }

  private static Map<String, Object> errorResponse(String code, String requestId) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("error", code);
    response.put("requestId", requestId);
    return response;
  }

  private static String requestId(Context context) {
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
