package com.julioserpone.srixadessigner;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.julioserpone.srixadessigner.config.SignerConfig;
import com.julioserpone.srixadessigner.crypto.CertificateLoader;
import com.julioserpone.srixadessigner.crypto.SigningIdentity;
import com.julioserpone.srixadessigner.crypto.TestCertificateFactory;
import com.julioserpone.srixadessigner.crypto.TestCertificateFactory.TestP12;
import com.julioserpone.srixadessigner.xml.SecureXmlParser;
import com.julioserpone.srixadessigner.xml.XmlSignatureVerifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class XadesBesSignerTest {

  private static SigningIdentity identity;

  @BeforeAll
  static void loadIdentity() {
    TestP12 p12 = TestCertificateFactory.create();
    identity = CertificateLoader.load(p12.pkcs12(), p12.password(), null);
  }

  @Test
  void signsWithTheSriXadesBesProfile() throws Exception {
    XadesBesSigner signer = new XadesBesSigner(SignerConfig.defaults());

    byte[] signed = signer.sign(TestXml.FACTURA.getBytes(StandardCharsets.UTF_8), identity);
    String xml = new String(signed, StandardCharsets.UTF_8);

    assertTrue(xml.contains("rsa-sha1"));
    assertTrue(xml.contains("http://www.w3.org/2000/09/xmldsig#sha1"));
    assertTrue(xml.contains("http://uri.etsi.org/01903/v1.3.2#"));
    assertFalse(xml.contains("SigningCertificateV2"));

    Document document = new SecureXmlParser().parse(signed);
    assertEquals(1, document.getElementsByTagNameNS(TestXml.XMLDSIG_NS, "Signature").getLength());
    assertEquals(1, document.getElementsByTagNameNS(TestXml.XMLDSIG_NS, "KeyInfo").getLength());
    assertEquals(
        1, document.getElementsByTagNameNS(TestXml.XADES_132_NS, "SigningCertificate").getLength());
    assertEquals(
        0,
        document.getElementsByTagNameNS(TestXml.XADES_132_NS, "SigningCertificateV2").getLength());
    assertEquals(
        1, document.getElementsByTagNameNS(TestXml.XADES_132_NS, "SignedProperties").getLength());
    NodeList dataObjectFormats =
        document.getElementsByTagNameNS(TestXml.XADES_132_NS, "DataObjectFormat");
    assertEquals(1, dataObjectFormats.getLength());
    Element dataObjectFormat = (Element) dataObjectFormats.item(0);
    assertEquals("#r-comprobante", dataObjectFormat.getAttribute("ObjectReference"));
    assertEquals(
        1,
        dataObjectFormat.getElementsByTagNameNS(TestXml.XADES_132_NS, "Description").getLength());

    Element certDigest =
        (Element) document.getElementsByTagNameNS(TestXml.XADES_132_NS, "CertDigest").item(0);
    String digestValue =
        certDigest
            .getElementsByTagNameNS(TestXml.XMLDSIG_NS, "DigestValue")
            .item(0)
            .getTextContent();
    assertArrayEquals(
        MessageDigest.getInstance("SHA-1").digest(identity.certificate().getEncoded()),
        Base64.getDecoder().decode(digestValue));

    Element keyInfo =
        (Element) document.getElementsByTagNameNS(TestXml.XMLDSIG_NS, "KeyInfo").item(0);
    String keyInfoId = keyInfo.getAttribute("Id");
    assertFalse(keyInfoId.isBlank());
    boolean keyInfoIsSigned = false;
    NodeList references = document.getElementsByTagNameNS(TestXml.XMLDSIG_NS, "Reference");
    for (int i = 0; i < references.getLength(); i++) {
      Element reference = (Element) references.item(i);
      keyInfoIsSigned |= ("#" + keyInfoId).equals(reference.getAttribute("URI"));
    }
    assertTrue(keyInfoIsSigned, "KeyInfo must be covered by a reference");

    assertFalse(
        document
            .getElementsByTagNameNS(TestXml.XMLDSIG_NS, "SignatureValue")
            .item(0)
            .getTextContent()
            .isBlank());

    new XmlSignatureVerifier().verify(signed, identity.certificate());
  }

  @Test
  void referencesTheRootIdWithEnvelopedTransform() {
    XadesBesSigner signer = new XadesBesSigner(SignerConfig.defaults());
    byte[] signed = signer.sign(TestXml.FACTURA.getBytes(StandardCharsets.UTF_8), identity);
    Document document = new SecureXmlParser().parse(signed);

    NodeList references = document.getElementsByTagNameNS(TestXml.XMLDSIG_NS, "Reference");
    boolean foundRootReference = false;
    for (int i = 0; i < references.getLength(); i++) {
      Element reference = (Element) references.item(i);
      if (!"#comprobante".equals(reference.getAttribute("URI"))) {
        continue;
      }
      foundRootReference = true;
      NodeList transforms = reference.getElementsByTagNameNS(TestXml.XMLDSIG_NS, "Transform");
      boolean hasEnvelopedTransform = false;
      for (int j = 0; j < transforms.getLength(); j++) {
        Element transform = (Element) transforms.item(j);
        hasEnvelopedTransform |=
            "http://www.w3.org/2000/09/xmldsig#enveloped-signature"
                .equals(transform.getAttribute("Algorithm"));
      }
      assertTrue(hasEnvelopedTransform, "The root reference must use enveloped-signature");
    }
    assertTrue(foundRootReference, "The root id must be referenced");
  }

  @Test
  void detectsContentTampering() {
    XadesBesSigner signer = new XadesBesSigner(SignerConfig.defaults());
    byte[] signed = signer.sign(TestXml.FACTURA.getBytes(StandardCharsets.UTF_8), identity);
    String tampered =
        new String(signed, StandardCharsets.UTF_8)
            .replace("PRODUCTO DE PRUEBA", "PRODUCTO ALTERADO");

    assertThrows(
        SigningException.class,
        () ->
            new XmlSignatureVerifier()
                .verify(tampered.getBytes(StandardCharsets.UTF_8), identity.certificate()));
  }

  @Test
  void rejectsDtdAndExternalEntities() {
    XadesBesSigner signer = new XadesBesSigner(SignerConfig.defaults());
    String xml =
        """
        <!DOCTYPE factura [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
        <factura id="comprobante">&xxe;</factura>
        """;

    assertThrows(
        InvalidXmlException.class,
        () -> signer.sign(xml.getBytes(StandardCharsets.UTF_8), identity));
  }

  @Test
  void rejectsMissingRootId() {
    XadesBesSigner signer = new XadesBesSigner(SignerConfig.defaults());
    String xml = "<factura><infoTributaria/></factura>";

    assertThrows(
        InvalidXmlException.class,
        () -> signer.sign(xml.getBytes(StandardCharsets.UTF_8), identity));
  }

  @Test
  void rejectsUnknownRootElement() {
    XadesBesSigner signer = new XadesBesSigner(SignerConfig.defaults());
    String xml = "<otro id=\"comprobante\"/>";

    assertThrows(
        InvalidXmlException.class,
        () -> signer.sign(xml.getBytes(StandardCharsets.UTF_8), identity));
  }

  @Test
  void rejectsAnAlreadySignedDocument() {
    XadesBesSigner signer = new XadesBesSigner(SignerConfig.defaults());
    byte[] signed = signer.sign(TestXml.FACTURA.getBytes(StandardCharsets.UTF_8), identity);

    assertThrows(InvalidXmlException.class, () -> signer.sign(signed, identity));
  }

  @Test
  void rejectsPayloadsOverTheConfiguredLimit() {
    SignerConfig smallConfig =
        new SignerConfig(
            32, SignerConfig.DEFAULT_ALLOWED_ROOTS, true, true, null, Duration.ofMinutes(5));
    XadesBesSigner signer = new XadesBesSigner(smallConfig);

    assertThrows(
        PayloadTooLargeException.class,
        () -> signer.sign(TestXml.FACTURA.getBytes(StandardCharsets.UTF_8), identity));
  }
}
