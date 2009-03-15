<?xml version="1.0" encoding="utf-8" ?>

<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
                xmlns="http://www.w3.org/1999/xhtml">
  <xsl:output
    doctype-public="-//W3C//DTD XHTML 1.1//EN"
    doctype-system="http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd"
    method="xhtml" version="1.0" media-type="text/html" />

  <xsl:template match="/error">
    <html>
      <head>
        <title><xsl:value-of select="title"/></title>
      </head>
      <body>
        <h1><xsl:value-of select="title"/></h1>

        <p>
          <xsl:value-of select="message"/>
        </p>
      </body>
    </html>
  </xsl:template>

  <xsl:template match="/services">
    <html>
      <head>
        <title>Available services</title>
      </head>
      <body>
        <h1>Available services</h1>

        <ul>
          <xsl:apply-templates select="service"/>
        </ul>
      </body>
    </html>
  </xsl:template>

  <xsl:template match="services/service">
    <li><xsl:value-of select="@type"/> service "<xsl:value-of select="@name"/> "at
        <a href="{@href}"><xsl:value-of select="@href"/></a></li>
  </xsl:template>

</xsl:stylesheet>
