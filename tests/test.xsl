<?xml version="1.0" encoding="utf-8" ?>

<xsl:stylesheet version="2.0" 
		xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
		xmlns:xhtml="http://www.w3.org/1999/xhtml">

  <!-- Identity transform -->

  <xsl:output 
	version="1.1"
	standalone="yes"
	cdata-section-elements="xhtml:p"
	indent="yes"
	doctype-system="http://apa.com"
	encoding="iso-8859-1" 
	method="html"
	media-type="text/html"
	omit-xml-declaration="no"/>

  <xsl:template match="@*|node()">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>


  <!-- Add class="red" to all <p> elements -->

  <xsl:template match="xhtml:p">
    <xsl:copy>
      <xsl:attribute name="class">red</xsl:attribute>
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="xhtml:span[@id='ext-java-func']">
    <xsl:copy xmlns:date="java.util.Date">
      <xsl:value-of select="date:get-year(date:new())"/>
    </xsl:copy>
  </xsl:template>

</xsl:stylesheet>
