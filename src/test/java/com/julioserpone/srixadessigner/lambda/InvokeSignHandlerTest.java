package com.julioserpone.srixadessigner.lambda;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.julioserpone.srixadessigner.TestXml;
import com.julioserpone.srixadessigner.config.SignerConfig;
import com.julioserpone.srixadessigner.crypto.CertificateLoadException;
import com.julioserpone.srixadessigner.crypto.CertificateLoader;
import com.julioserpone.srixadessigner.crypto.SigningIdentity;
import com.julioserpone.srixadessigner.crypto.TenantSigningIdentityProvider;
import com.julioserpone.srixadessigner.crypto.TestCertificateFactory;
import com.julioserpone.srixadessigner.crypto.TestCertificateFactory.TestP12;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InvokeSignHandlerTest {

  private static SigningIdentity identity;

  @BeforeAll
  static void loadIdentity() {
    TestP12 p12 = TestCertificateFactory.create();
    identity = CertificateLoader.load(p12.pkcs12(), p12.password(), null);
  }

  @Test
  void signsAValidTenantRequest() {
    InvokeSignHandler handler = new InvokeSignHandler(SignerConfig.defaults(), provider());
    Map<String, Object> request = request(UUID.randomUUID().toString(), "changeit");

    Map<String, Object> response = handler.handleRequest(request, null);

    assertNotNull(response.get("signedXmlBase64"));
    assertNotNull(response.get("requestId"));
    String signedXml =
        new String(
            Base64.getDecoder().decode((String) response.get("signedXmlBase64")),
            StandardCharsets.UTF_8);
    assertTrue(signedXml.contains("<ds:Signature"));
    assertTrue(signedXml.contains("SignedProperties"));
  }

  @Test
  void clearsThePasswordArrayAfterUse() {
    AtomicReference<char[]> capturedPassword = new AtomicReference<>();
    TenantSigningIdentityProvider provider =
        (tenantUuid, password) -> {
          capturedPassword.set(password);
          return identity;
        };
    InvokeSignHandler handler = new InvokeSignHandler(SignerConfig.defaults(), provider);

    handler.handleRequest(request(UUID.randomUUID().toString(), "changeit"), null);

    char[] password = capturedPassword.get();
    assertNotNull(password);
    assertArrayEquals(new char[password.length], password);
  }

  @Test
  void rejectsMissingFields() {
    InvokeSignHandler handler = new InvokeSignHandler(SignerConfig.defaults(), provider());
    Map<String, Object> request = request(UUID.randomUUID().toString(), "changeit");
    request.remove("password");

    Map<String, Object> response = handler.handleRequest(request, null);

    assertEquals("invalid_request", response.get("error"));
  }

  @Test
  void rejectsInvalidBase64() {
    InvokeSignHandler handler = new InvokeSignHandler(SignerConfig.defaults(), provider());
    Map<String, Object> request = request(UUID.randomUUID().toString(), "changeit");
    request.put("xmlBase64", "not base64!");

    Map<String, Object> response = handler.handleRequest(request, null);

    assertEquals("invalid_request", response.get("error"));
  }

  @Test
  void rejectsOversizedPayloadsBeforeDecoding() {
    SignerConfig smallConfig =
        new SignerConfig(
            32, SignerConfig.DEFAULT_ALLOWED_ROOTS, true, true, null, Duration.ofMinutes(5));
    InvokeSignHandler handler = new InvokeSignHandler(smallConfig, provider());

    Map<String, Object> response =
        handler.handleRequest(request(UUID.randomUUID().toString(), "changeit"), null);

    assertEquals("payload_too_large", response.get("error"));
  }

  @Test
  void mapsCertificateFailuresToAnUnavailableCode() {
    TenantSigningIdentityProvider provider =
        (tenantUuid, password) -> {
          throw new CertificateLoadException("not found");
        };
    InvokeSignHandler handler = new InvokeSignHandler(SignerConfig.defaults(), provider);

    Map<String, Object> response =
        handler.handleRequest(request(UUID.randomUUID().toString(), "changeit"), null);

    assertEquals("certificate_unavailable", response.get("error"));
  }

  @Test
  void mapsInvalidTenantIdentifiersToInvalidRequest() {
    TenantSigningIdentityProvider provider =
        (tenantUuid, password) -> {
          throw new IllegalArgumentException("bad tenant");
        };
    InvokeSignHandler handler = new InvokeSignHandler(SignerConfig.defaults(), provider);

    Map<String, Object> response = handler.handleRequest(request("not-a-uuid", "changeit"), null);

    assertEquals("invalid_request", response.get("error"));
  }

  private static TenantSigningIdentityProvider provider() {
    return (tenantUuid, password) -> identity;
  }

  private static Map<String, Object> request(String tenantUuid, String password) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("tenantUuid", tenantUuid);
    request.put("password", password);
    request.put(
        "xmlBase64",
        Base64.getEncoder().encodeToString(TestXml.FACTURA.getBytes(StandardCharsets.UTF_8)));
    return request;
  }
}
