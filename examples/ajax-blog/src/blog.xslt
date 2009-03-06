
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
                xmlns="http://www.w3.org/1999/xhtml">
  <xsl:output
    doctype-public="-//W3C//DTD XHTML 1.1//EN"
    doctype-system="http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd"
    method="xhtml" version="1.0" media-type="text/html" indent="no" />

  <!-- The Admin GUI -->
  <xsl:template match="/admin">
    <html>
      <head>
	<title>The Ajax Blog: Administration</title>
	<link rel="stylesheet" type="text/css" 
	      href="http://extjs.cachefly.net/ext-2.2.1/resources/css/ext-all.css" />

	<script type="text/javascript" 
		src="http://extjs.cachefly.net/builds/ext-cdn-771.js"></script>

	<!-- Set 'postsURI' global variable for admin-gui.js -->
	<script type="text/javascript">var postsURI = "<xsl:value-of select='postsURI'/>";</script>
	<script type="text/javascript" src="{resourceURI}admin-gui.js"></script>
      </head>

      <body>
	<div id="html-to-xhtml" style="visible: none"/>
      </body>
    </html>
  </xsl:template>


  <!-- The Blog view -->
  <xsl:template match="/blog">
    <html>
      <head>
	<title>The Ajax Blog</title>
      </head>
      <body>
	<h1>The Ajax Blog</h1>
	<p><a href="{adminURI}">Administration</a></p>

	<xsl:apply-templates select="posts/post" />
      </body>
    </html>
  </xsl:template>

  <!-- The Blog post view -->
  <xsl:template match="/blog-entry">
    <html>
      <head>
	<title><xsl:value-of select="post/title"/></title>
      </head>
      <body>
	<h1><xsl:value-of select="post/title"/></h1>
	<p><a href="{adminURI}">Administration</a></p>

	<p><xsl:apply-templates select="post/body/node()" mode="html-to-xhtml" /></p>

	<h2>Comments</h2>
	<xsl:apply-templates select="comments/comment" />
      </body>
    </html>
  </xsl:template>

  <!-- The list-of-posts view -->
  <xsl:template match="posts/post">
    <h2><a href="{@href}"><xsl:value-of select="title" /></a></h2>
    <p><xsl:apply-templates select="body/node()" mode="html-to-xhtml" /></p>
  </xsl:template>

  <!-- The list-of-comments view -->
  <xsl:template match="comments/comment">
    <p><xsl:apply-templates select="body/node()" mode="html-to-xhtml" /></p>
    <hr/>
  </xsl:template>

  <!-- Rules to change the namespace of all elements to XHTML and make them lowercase -->
  <xsl:template match="@*|comment()|processing-instruction()|text()" mode="html-to-xhtml">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()" mode="html-to-xhtml"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="element()" mode="html-to-xhtml">
    <xsl:element name="{lower-case(local-name())}" namespace="http://www.w3.org/1999/xhtml">
      <xsl:apply-templates select="@*|node()" mode="html-to-xhtml"/>
    </xsl:element>
  </xsl:template>


</xsl:stylesheet>
