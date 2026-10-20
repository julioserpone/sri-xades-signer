package com.julioserpone.srixadessigner;

/** Small XML fixtures used by tests. */
public final class TestXml {

  public static final String XMLDSIG_NS = "http://www.w3.org/2000/09/xmldsig#";
  public static final String XADES_132_NS = "http://uri.etsi.org/01903/v1.3.2#";

  public static final String FACTURA =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <factura id="comprobante" version="1.0.0">
        <infoTributaria>
          <ambiente>1</ambiente>
          <tipoEmision>1</tipoEmision>
          <razonSocial>EMPRESA DE PRUEBA</razonSocial>
          <nombreComercial>PRUEBA</nombreComercial>
          <ruc>1790012345001</ruc>
          <claveAcceso>2010202601179001234500110010010000000011234567811</claveAcceso>
          <codDoc>01</codDoc>
          <estab>001</estab>
          <ptoEmi>001</ptoEmi>
          <secuencial>000000001</secuencial>
          <dirMatriz>QUITO</dirMatriz>
        </infoTributaria>
        <infoFactura>
          <fechaEmision>20/10/2026</fechaEmision>
          <obligadoContabilidad>NO</obligadoContabilidad>
          <tipoIdentificacionComprador>05</tipoIdentificacionComprador>
          <razonSocialComprador>CLIENTE DE PRUEBA</razonSocialComprador>
          <identificacionComprador>1712345678</identificacionComprador>
          <totalSinImpuestos>10.00</totalSinImpuestos>
          <totalDescuento>0.00</totalDescuento>
          <totalConImpuestos>
            <totalImpuesto>
              <codigo>2</codigo>
              <codigoPorcentaje>4</codigoPorcentaje>
              <baseImponible>10.00</baseImponible>
              <valor>1.50</valor>
            </totalImpuesto>
          </totalConImpuestos>
          <propina>0.00</propina>
          <importeTotal>11.50</importeTotal>
          <moneda>DOLAR</moneda>
        </infoFactura>
        <detalles>
          <detalle>
            <codigoPrincipal>001</codigoPrincipal>
            <descripcion>PRODUCTO DE PRUEBA</descripcion>
            <cantidad>1.00</cantidad>
            <precioUnitario>10.00</precioUnitario>
            <descuento>0.00</descuento>
            <precioTotalSinImpuesto>10.00</precioTotalSinImpuesto>
          </detalle>
        </detalles>
      </factura>
      """;

  private TestXml() {}
}
