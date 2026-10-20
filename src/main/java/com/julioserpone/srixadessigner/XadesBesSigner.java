package com.julioserpone.srixadessigner;

import com.julioserpone.srixadessigner.config.SignerConfig;
import com.julioserpone.srixadessigner.crypto.SigningIdentity;
import com.julioserpone.srixadessigner.xml.SecureXmlParser;
import com.julioserpone.srixadessigner.xml.XmlSignatureVerifier;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.EncryptionAlgorithm;
import eu.europa.esig.dss.enumerations.MimeTypeEnum;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignaturePackaging;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.xades.XAdESSignatureParameters;
import eu.europa.esig.dss.xades.dataobject.DSSDataObjectFormat;
import eu.europa.esig.dss.xades.reference.DSSReference;
import eu.europa.esig.dss.xades.reference.EnvelopedSignatureTransform;
import eu.europa.esig.dss.xades.signature.XAdESService;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.cert.CertificateException;
import java.util.List;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Creates an enveloped XAdES-BES signature compatible with the SRI profile:
 *
 * <ul>
 *   <li>XAdES 1.3.2 namespace and {@code SigningCertificate} (v1),
 *   <li>RSA-SHA1 signature and SHA-1 references,
 *   <li>{@code KeyInfo} included and signed,
 *   <li>UTF-8 XML.
 * </ul>
 *
 * <p>The signer does not replace SRI certification. Validate the generated XML in the SRI
 * certification environment before using it in production.
 */
public final class XadesBesSigner {

  private static final String C14N = "http://www.w3.org/TR/2001/REC-xml-c14n-20010315";
  private static final BouncyCastleProvider BC_PROVIDER = new BouncyCastleProvider();

  private final SignerConfig config;
  private final SecureXmlParser xmlParser = new SecureXmlParser();
  private final XmlSignatureVerifier signatureVerifier = new XmlSignatureVerifier();

  public XadesBesSigner(SignerConfig config) {
    this.config = config;
  }

  public byte[] sign(byte[] xml, SigningIdentity identity) {
    if (xml == null || xml.length == 0) {
      throw new InvalidXmlException("The XML payload is empty");
    }
    if (xml.length > config.maxXmlBytes()) {
      throw new PayloadTooLargeException(
          "The XML payload exceeds the configured maximum size of "
              + config.maxXmlBytes()
              + " bytes");
    }

    validateCertificate(identity);
    String rootId = validateXml(xml);
    byte[] signed = createSignature(xml, identity, rootId);

    if (config.verifyAfterSign()) {
      signatureVerifier.verify(signed, identity.certificate());
    }
    return signed;
  }

  private void validateCertificate(SigningIdentity identity) {
    if (config.requireCertificateValidity()) {
      try {
        identity.certificate().checkValidity();
      } catch (CertificateException e) {
        throw new SigningException("The signing certificate is expired or not yet valid", e);
      }
    }
  }

  private String validateXml(byte[] xml) {
    Document document = xmlParser.parse(xml);
    Element root = document.getDocumentElement();
    if (root == null) {
      throw new InvalidXmlException("The XML payload has no root element");
    }

    String rootName = root.getLocalName() == null ? root.getNodeName() : root.getLocalName();
    if (!config.allowedRoots().contains(rootName)) {
      throw new InvalidXmlException("The XML root element is not allowed: " + rootName);
    }

    String id = root.getAttribute("id");
    if (id == null || id.isBlank()) {
      id = root.getAttribute("Id");
    }
    if (id == null || id.isBlank()) {
      throw new InvalidXmlException("The XML root element must have an id attribute");
    }

    if (document.getElementsByTagNameNS("*", "Signature").getLength() > 0) {
      throw new InvalidXmlException("The XML already contains a signature");
    }
    return id;
  }

  private byte[] createSignature(byte[] xml, SigningIdentity identity, String rootId) {
    try {
      XAdESSignatureParameters parameters = new XAdESSignatureParameters();
      parameters.setSignatureLevel(SignatureLevel.XAdES_BASELINE_B);
      parameters.setSignaturePackaging(SignaturePackaging.ENVELOPED);
      parameters.setDigestAlgorithm(DigestAlgorithm.SHA1);
      parameters.setReferenceDigestAlgorithm(DigestAlgorithm.SHA1);
      parameters.setSigningCertificateDigestMethod(DigestAlgorithm.SHA1);
      parameters.setEncryptionAlgorithm(EncryptionAlgorithm.RSA);
      parameters.setEn319132(false);
      parameters.setSignKeyInfo(true);
      parameters.setPrettyPrint(false);
      parameters.setSignedInfoCanonicalizationMethod(C14N);
      parameters.setSignedPropertiesCanonicalizationMethod(C14N);
      parameters.setKeyInfoCanonicalizationMethod(C14N);

      CertificateToken signingCertificate = new CertificateToken(identity.certificate());
      List<CertificateToken> certificateChain =
          identity.certificateChain().stream().map(CertificateToken::new).toList();
      parameters.setSigningCertificate(signingCertificate);
      parameters.setCertificateChain(certificateChain);

      DSSDocument toSignDocument = new InMemoryDocument(xml, "comprobante.xml", MimeTypeEnum.XML);

      DSSReference contentReference = new DSSReference();
      contentReference.setId("r-comprobante");
      contentReference.setUri("#" + rootId);
      contentReference.setContents(toSignDocument);
      contentReference.setDigestMethodAlgorithm(DigestAlgorithm.SHA1);
      contentReference.setTransforms(List.of(new EnvelopedSignatureTransform()));
      parameters.setReferences(List.of(contentReference));

      DSSDataObjectFormat contentFormat = new DSSDataObjectFormat();
      contentFormat.setDescription("contenido comprobante");
      contentFormat.setMimeType("text/xml");
      contentFormat.setObjectReference("#" + contentReference.getId());
      parameters.setDataObjectFormatList(List.of(contentFormat));

      XAdESService service = new XAdESService(new CommonCertificateVerifier());

      ToBeSigned dataToSign = service.getDataToSign(toSignDocument, parameters);
      Signature signature = Signature.getInstance("SHA1withRSA", BC_PROVIDER);
      signature.initSign(identity.privateKey());
      signature.update(dataToSign.getBytes());
      SignatureValue signatureValue =
          new SignatureValue(SignatureAlgorithm.RSA_SHA1, signature.sign());

      DSSDocument signedDocument = service.signDocument(toSignDocument, parameters, signatureValue);
      try (InputStream input = signedDocument.openStream()) {
        return input.readAllBytes();
      }
    } catch (GeneralSecurityException | IOException e) {
      throw new SigningException("Could not create the XAdES-BES signature", e);
    }
  }
}
