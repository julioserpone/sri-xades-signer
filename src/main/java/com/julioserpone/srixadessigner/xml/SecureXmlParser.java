package com.julioserpone.srixadessigner.xml;

import com.julioserpone.srixadessigner.InvalidXmlException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Hardened XML parser for untrusted input.
 *
 * <p>DTDs, external entities, XInclude and external schemas are disabled. This prevents XXE and
 * entity-expansion attacks before the XML reaches DSS.
 */
public final class SecureXmlParser {

  public Document parse(byte[] xml) {
    if (xml == null || xml.length == 0) {
      throw new InvalidXmlException("The XML payload is empty");
    }

    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setValidating(false);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

      DocumentBuilder builder = factory.newDocumentBuilder();
      builder.setEntityResolver(
          (publicId, systemId) -> {
            throw new SAXException("External entities are not allowed");
          });
      builder.setErrorHandler(
          new DefaultHandler() {
            @Override
            public void error(SAXParseException exception) throws SAXException {
              throw exception;
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
              throw exception;
            }
          });

      return builder.parse(new ByteArrayInputStream(xml));
    } catch (ParserConfigurationException | SAXException | IOException e) {
      throw new InvalidXmlException("The XML payload is not valid or is not allowed", e);
    }
  }
}
