package com.julioserpone.srixadessigner.lambda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.julioserpone.srixadessigner.TestXml;
import com.julioserpone.srixadessigner.config.SignerConfig;
import com.julioserpone.srixadessigner.crypto.CertificateLoader;
import com.julioserpone.srixadessigner.crypto.SigningIdentity;
import com.julioserpone.srixadessigner.crypto.TestCertificateFactory;
import com.julioserpone.srixadessigner.crypto.TestCertificateFactory.TestP12;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SignHandlerTest {

  private static SigningIdentity identity;

  @BeforeAll
  static void loadIdentity() {
    TestP12 p12 = TestCertificateFactory.create();
    identity = CertificateLoader.load(p12.pkcs12(), p12.password(), null);
  }

  @Test
  void signsAValidRequest() {
    SignHandler handler = new SignHandler(SignerConfig.defaults(), () -> identity);
    APIGatewayProxyRequestEvent request = request("POST", TestXml.FACTURA);

    APIGatewayProxyResponseEvent response = handler.handleRequest(request, null);

    assertEquals(200, response.getStatusCode());
    assertNotNull(response.getBody());
    assertTrue(response.getBody().contains("<ds:Signature"));
    assertTrue(response.getBody().contains("SignedProperties"));
    assertEquals("application/xml; charset=utf-8", response.getHeaders().get("Content-Type"));
    assertEquals("req-test", response.getHeaders().get("X-Request-Id"));
  }

  @Test
  void acceptsBase64EncodedBody() {
    SignHandler handler = new SignHandler(SignerConfig.defaults(), () -> identity);
    APIGatewayProxyRequestEvent request = request("POST", "");
    request.setBody(
        Base64.getEncoder().encodeToString(TestXml.FACTURA.getBytes(StandardCharsets.UTF_8)));
    request.setIsBase64Encoded(true);

    APIGatewayProxyResponseEvent response = handler.handleRequest(request, null);

    assertEquals(200, response.getStatusCode());
  }

  @Test
  void rejectsNonPostMethods() {
    SignHandler handler = new SignHandler(SignerConfig.defaults(), () -> identity);

    APIGatewayProxyResponseEvent response =
        handler.handleRequest(request("GET", TestXml.FACTURA), null);

    assertEquals(405, response.getStatusCode());
  }

  @Test
  void rejectsUnknownContentTypes() {
    SignHandler handler = new SignHandler(SignerConfig.defaults(), () -> identity);
    APIGatewayProxyRequestEvent request = request("POST", TestXml.FACTURA);
    request.setHeaders(Map.of("Content-Type", "application/json"));

    APIGatewayProxyResponseEvent response = handler.handleRequest(request, null);

    assertEquals(415, response.getStatusCode());
  }

  @Test
  void rejectsDtd() {
    SignHandler handler = new SignHandler(SignerConfig.defaults(), () -> identity);
    String xml =
        """
        <!DOCTYPE factura [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
        <factura id="comprobante">&xxe;</factura>
        """;

    APIGatewayProxyResponseEvent response = handler.handleRequest(request("POST", xml), null);

    assertEquals(400, response.getStatusCode());
  }

  @Test
  void rejectsOversizedRequests() {
    SignerConfig smallConfig =
        new SignerConfig(
            32, SignerConfig.DEFAULT_ALLOWED_ROOTS, true, true, null, Duration.ofMinutes(5));
    SignHandler handler = new SignHandler(smallConfig, () -> identity);

    APIGatewayProxyResponseEvent response =
        handler.handleRequest(request("POST", TestXml.FACTURA), null);

    assertEquals(413, response.getStatusCode());
  }

  private static APIGatewayProxyRequestEvent request(String method, String body) {
    APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
    request.setHttpMethod(method);
    request.setHeaders(Map.of("Content-Type", "application/xml; charset=utf-8"));
    request.setBody(body);
    request.setRequestContext(
        new APIGatewayProxyRequestEvent.ProxyRequestContext().withRequestId("req-test"));
    return request;
  }
}
