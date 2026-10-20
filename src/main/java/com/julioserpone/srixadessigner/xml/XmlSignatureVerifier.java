package com.julioserpone.srixadessigner.xml;

import com.julioserpone.srixadessigner.SigningException;
import java.security.cert.X509Certificate;
import java.util.List;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Verifies the generated XMLDSig core and the XAdES profile expected by the SRI. */
public final class XmlSignatureVerifier {

  private static final String XMLDSIG_NS = "http://www.w3.org/2000/09/xmldsig#";
  private static final String SIGNED_PROPERTIES_TYPE = "http://uri.etsi.org/01903#SignedProperties";

  private final SecureXmlParser parser = new SecureXmlParser();

  public void verify(byte[] signedXml, X509Certificate certificate) {
    Document document = parser.parse(signedXml);
    NodeList signatures = document.getElementsByTagNameNS(XMLDSIG_NS, "Signature");
    if (signatures.getLength() != 1) {
      throw new SigningException("The signed XML must contain exactly one ds:Signature");
    }

    try {
      DOMValidateContext context =
          new DOMValidateContext(certificate.getPublicKey(), signatures.item(0));
      // SHA-1 is required by the SRI specification. It is intentionally not "secure validation".
      context.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.FALSE);
      registerIdAttributes(document, context);

      XMLSignature signature =
          XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(context);
      assertSriProfile(signature);

      if (!signature.validate(context)) {
        throw new SigningException("The generated XML signature did not validate");
      }
    } catch (SigningException e) {
      throw e;
    } catch (Exception e) {
      throw new SigningException("Could not verify the generated XML signature", e);
    }
  }

  private static void registerIdAttributes(Document document, DOMValidateContext context) {
    NodeList elements = document.getElementsByTagName("*");
    for (int i = 0; i < elements.getLength(); i++) {
      Element element = (Element) elements.item(i);
      if (element.hasAttribute("Id")) {
        context.setIdAttributeNS(element, null, "Id");
      }
      if (element.hasAttribute("id")) {
        context.setIdAttributeNS(element, null, "id");
      }
    }
  }

  private static void assertSriProfile(XMLSignature signature) {
    if (!SignatureMethod.RSA_SHA1.equals(
        signature.getSignedInfo().getSignatureMethod().getAlgorithm())) {
      throw new SigningException("The signature method is not RSA-SHA1");
    }

    List<Reference> references = signature.getSignedInfo().getReferences();
    boolean hasSignedProperties = false;
    for (Reference reference : references) {
      if (!DigestMethod.SHA1.equals(reference.getDigestMethod().getAlgorithm())) {
        throw new SigningException("A reference does not use SHA-1");
      }
      if (SIGNED_PROPERTIES_TYPE.equals(reference.getType())) {
        hasSignedProperties = true;
      }
    }
    if (!hasSignedProperties) {
      throw new SigningException("The signature does not protect xades:SignedProperties");
    }
  }
}
