<?xml version="1.0" encoding="utf-8" ?>

<xsl:stylesheet version="1.0" 
		xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
		xmlns:xhtml="http://www.w3.org/1999/xhtml">

  <!-- Identity transform -->

  <xsl:output encoding="iso-8859-1" media-type="text/html"/>

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

</xsl:stylesheet>
