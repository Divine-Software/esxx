<?xml version="1.0" encoding="utf-8" ?>

<xsl:stylesheet version="2.0" 
		xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
		xmlns:xhtml="http://www.w3.org/1999/xhtml">

  <!-- Identity transform -->
  <xsl:param name="apa">monkey</xsl:param>
  <xsl:param name="xml"><empty><xml/></empty></xsl:param>

  <xsl:output 
	version="4.0"
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
      <xsl:attribute name="apa"><xsl:value-of select="$apa" /></xsl:attribute>
      <xsl:copy-of select="$xml" />
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>

  <!-- Test various Java/JS functions -->

  <xsl:template match="xhtml:span[@id='ext-java-func']">
    <xsl:copy xmlns:date="java.util.Date">
      <xsl:value-of select="date:get-year(date:new())"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="xhtml:span[@id='ext-js-func']">
    <xsl:copy xmlns:date="javascript:currentDate">
      <xsl:value-of select="date:getYear()"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="xhtml:span[@id='ext-js-test1']">
    <xsl:copy xmlns:test="javascript:">
      <xsl:value-of select="test:test(1+2, 'banan', ., @*)"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="xhtml:span[@id='ext-js-test2']">
    <xsl:copy xmlns:test="javascript:myapp">
      <xsl:value-of select="test:xsltCallback()"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="xhtml:span[@id='ext-js-test3']">
    <!-- The xmlTest() function returns XML -->
    <xsl:copy-of xmlns:test="javascript:" select="test:xmlTest()"/>
  </xsl:template>

  <xsl:template match="xhtml:pre[@class='debug']">
     <xsl:copy>
       <!-- Extract comment(s) after the document element -->
       <xsl:value-of select="/*/following::comment()"/>
     </xsl:copy>
  </xsl:template>

</xsl:stylesheet>
