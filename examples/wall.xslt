<?xml version="1.0" encoding="utf-8" ?>

<xsl:stylesheet version="2.0" 
		xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
		xmlns:xs="http://www.w3.org/2001/XMLSchema"
		xmlns="http://www.w3.org/1999/xhtml">
  <xsl:output
    doctype-public="-//W3C//DTD XHTML 1.1//EN"
    doctype-system="http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd"
    method="xhtml" version="1.1" media-type="text/html" />

  <!-- The wall -->
  <xsl:template match="/wall">
    <html>
      <head>
        <title>My Wall</title>
        <link rel="stylesheet" type="text/css" href="wall.css"/>
      </head>
      <body>
        <h1>My Wall</h1>
        <xsl:apply-templates select="entries"/>
    
        <xsl:if test="error">
         <p class="error">Error: <xsl:value-of select="error"/></p>
        </xsl:if>

        <xsl:apply-templates select="form"/>
      </body>
    </html>
  </xsl:template>

  <!-- Messages table -->
  <xsl:template match="entries">
  <h2>Messages</h2>
  <p>
  <table class="msg"> 
   <tr>
      <th>Date</th>
      <th>Name</th>
      <th>Message</th>
    </tr>
    <xsl:apply-templates select="entry"/>
  </table>
</p>
  </xsl:template>

  <!-- A message in the table -->
  <xsl:template match="entry">
    <tr class="{if (position() mod 2 = 1) then 'odd' else 'even'}">
      <td><xsl:value-of select="format-dateTime(xs:dateTime(translate(date, ' ', 'T')),
				'[MNn] [D1o], [h].[m01] [Pn]', 'en', (), ())"/></td>
      <td><xsl:value-of select="name"/></td>
      <td><xsl:value-of select="message"/></td>
    </tr>
  </xsl:template>

  <!-- New message form -->
  <xsl:template match="form">
    <h2>Post new message</h2>
    <form method="post">
      <table>
        <tr>
          <td>Your name</td>
          <td><input type="text" name="name" value="{name}"/></td>
        </tr>
        <tr>
          <td>Message</td>
          <td><textarea name="message"><xsl:value-of select="message"/></textarea></td>
        </tr>
        <tr>
	  <td>&#160;</td>
          <td><input type="submit" value="Add my message!"/></td>
        </tr>
      </table>
    </form>
  </xsl:template>
</xsl:stylesheet>
