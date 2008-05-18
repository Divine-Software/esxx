<?xml version="1.0" encoding="utf-8" ?>

<xsl:stylesheet version="2.0"
		xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
		xmlns="http://www.w3.org/1999/xhtml"
                xmlns:app="javascript:app"
                exclude-result-prefixes="app">

<xsl:variable name="title">ESXX App</xsl:variable>
<xsl:variable name="heading">Hello :: ESXX App</xsl:variable>
<xsl:variable name="greeting">Greetings, it is now</xsl:variable>
<xsl:variable name="priceincrease.heading">Price Increase :: ESXX App</xsl:variable>

  <xsl:output
    doctype-public="-//W3C//DTD XHTML 1.1//EN"
    doctype-system="http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd"
    method="xhtml" version="1.1" media-type="text/html" />

  <!-- Identity transform -->

  <xsl:template match="@*|node()">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>

  <!-- Common for all pages -->
  <xsl:template match="/">
    <html>
      <head>
        <title><xsl:value-of select="$title"/></title>
      </head>
      <body>
        <xsl:apply-templates/>
      </body>
    </html>
  </xsl:template>

  <!-- The front page -->
  <xsl:template match="/welcome">
    <h1><xsl:value-of select="$heading"/></h1>

    <p xml:space="preserve"><xsl:value-of select="$greeting"/> <xsl:value-of select="now"/></p>

    <h2>Products</h2>
    <ol>
      <xsl:apply-templates select="products/product"/>
    </ol>

    <p><a href="increasePrices">Increase Prices</a></p>
  </xsl:template>

  <!-- The product list -->
  <xsl:template match="products/product">
    <li xml:space="preserve"><xsl:value-of select="description"/> <i>$<xsl:value-of select="price"/></i></li>
  </xsl:template>


  <!-- The Price Increase form -->
  <xsl:template match="/increasePrices">
    <h1><xsl:value-of select="$priceincrease.heading"/></h1>

    <form method="post">
      <table width="95%" bgcolor="f8f8ff" border="0" cellspacing="0" cellpadding="5">
        <tr>
          <td alignment="right" width="20%">Increase (%):</td>
          <td width="20%">
            <input type="text" name="percentageValue" value="{percentageValue}" />
          </td>
          <td width="60%">
            <font color="red"><xsl:value-of select="errorMessage"/></font>
          </td>
        </tr>
      </table>

      <xsl:if test="errorMessage">
        <p><b>Please fix all errors!</b></p>
      </xsl:if>

      <input type="submit" alignment="center" value="Execute" />
    </form>

    <p><a href="main">Home</a></p>
  </xsl:template>

</xsl:stylesheet>
