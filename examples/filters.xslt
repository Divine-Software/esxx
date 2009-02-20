<?xml version="1.0" ?>

<xsl:stylesheet version="2.0" 
		xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:param name="base" />

  <xsl:output version="4.0"
	      method="html"
	      media-type="text/html" />

  <xsl:template match="@*|node()">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>


  <xsl:template match="/">
    <html>
      <body>
	<xsl:apply-templates select="*" />
      </body>
    </html>
  </xsl:template>

  <xsl:template match="index">
    <h1>Available resources</h1>

    <ol>
      <xsl:for-each select="resource">
	<li><a href="{$base}{@href}"><xsl:value-of select="." /></a></li>
      </xsl:for-each>
    </ol>
  </xsl:template>

  <xsl:template match="unauthorized">
    <h1>Unauthorized</h1>
    
    <p><xsl:value-of select="." /></p>
  </xsl:template>

  <xsl:template match="forbidden">
    <h1>Forbidden</h1>
    
    <p><xsl:value-of select="." /></p>
  </xsl:template>

  <xsl:template match="currentDate">
    <h1>Current date</h1>
    
    <p>The current date is <xsl:value-of select="concat(year, '-', month, '-', day)" /></p>
  </xsl:template>

</xsl:stylesheet>
