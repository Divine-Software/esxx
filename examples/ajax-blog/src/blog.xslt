
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
		xmlns:xs="http://www.w3.org/2001/XMLSchema"
		xmlns:my="urn:x-my"
                xmlns="http://www.w3.org/1999/xhtml">
  <xsl:output
    doctype-public="-//W3C//DTD XHTML 1.1//EN"
    doctype-system="http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd"
    method="xhtml" version="1.0" media-type="text/html" indent="no" />

  <!-- These parameters will be overridden by setXSLTParams() -->
  <xsl:param name="scriptURI" >/index.esxx/</xsl:param>
  <xsl:param name="resourceURI" >/</xsl:param>
  <xsl:param name="adminURI" >/admin.html</xsl:param>
  <xsl:param name="postsURI" >/posts/</xsl:param>

  <!-- The Admin GUI -->
  <xsl:template match="/admin">
    <html>
      <head>
	<title>The Ajax Blog: Administration</title>
	<link rel="stylesheet" type="text/css" 
	      href="http://extjs.cachefly.net/ext-2.2.1/resources/css/ext-all.css" />

	<script type="text/javascript" 
		src="http://extjs.cachefly.net/builds/ext-cdn-771.js"></script>

	<!-- Set some global variables for admin-gui.js -->
	<script type="text/javascript">
	  var scriptURI    = "<xsl:value-of select='$scriptURI'  />";
	  var resourceURI  = "<xsl:value-of select='$resourceURI'/>";
	  var adminURI     = "<xsl:value-of select='$adminURI'   />";
	  var postsURI     = "<xsl:value-of select='$postsURI'   />";
	</script>
	<script type="text/javascript" src="{$resourceURI}js/xml-utils.js"></script>
	<script type="text/javascript" src="{$resourceURI}js/admin-gui.js"></script>
      </head>

      <body/>
    </html>
  </xsl:template>


  <!-- The Blog view -->
  <xsl:template match="/blog">
    <html>
      <head>
	<title>The Ajax Blog</title>
	<link rel="stylesheet" type="text/css" href="{$resourceURI}css/main.css" />
      </head>

      <body>
        <div id="masthead">
          <div id="mast-content" class="clearfix">
            <div id="meta" class="clearfix">
	      <h1><a href="{$scriptURI}">The Ajax Blog</a></h1>
	      <p><a href="{$adminURI}">Administration</a></p>
	    </div>
	  
	    <div id="navcontainer">
	      <ul id="nav">
		<xsl:for-each select="posts/post[7 >= position()]">
		  <li>
		    <span><xsl:number format="I"/></span>
		    <a href="#p-{id}"><xsl:value-of select="my:truncate(title//text(), 50)" /></a>
		    <br/>
		  </li>
		</xsl:for-each>
	      </ul>
	    </div>

	    <div id="ajax-image">
	      <img src="{$resourceURI}img/ajax.jpg"/>
	    </div>
	  </div>

	  <div id="side-image"/>


	</div>

	<div id="content">
	  <xsl:apply-templates select="posts/post" />
	</div>
	<xsl:call-template name="footer" />
      </body>
    </html>
  </xsl:template>

  <!-- The Blog post/comments view -->
  <xsl:template match="/blog-entry">
    <html>
      <head>
	<title>The Ajax Blog: <xsl:value-of select="post/title"/></title>
	<link rel="stylesheet" type="text/css" href="{$resourceURI}css/main.css" />
      </head>

      <body>
        <div id="masthead">
          <div id="mast-content" class="clearfix">
            <div id="meta" class="clearfix">
	      <h1><a href="{$scriptURI}">The Ajax Blog</a>: <xsl:value-of select="post/title"/></h1>
	      <p><a href="{$adminURI}">Administration</a></p>
	    </div>
	  
	    <div id="navcontainer">
	      <ul id="nav">
		<xsl:for-each select="comments/comment[7 >= position()]">
		  <li>
		    <span><xsl:number format="I"/></span>
		    <a href="#c-{id}"><xsl:value-of select="my:truncate(body//text(), 50)"  /></a>
		    <br/>
		  </li>
		</xsl:for-each>
	      </ul>
	    </div>

	    <div id="ajax-image">
	      <img src="{$resourceURI}img/ajax.jpg"/>
	    </div>
	  </div>

	  <div id="side-image"/>
	</div>

	<div id="content">
	  <xsl:apply-templates select="post" />

	  <xsl:if test="comments/comment">
	    <h3>Comments</h3>
	    <xsl:apply-templates select="comments/comment" />
	  </xsl:if>

	  <xsl:apply-templates select="error" />

	  <h3>Add a new comment</h3>
	  <form method="POST">
	    <textarea name="comment" cols="80" rows="7"/>
	    <br/>
	    <input type="submit" value="Add comment"/>
	  </form>
	</div>
	<xsl:call-template name="footer" />
      </body>
    </html>
  </xsl:template>

  <!-- The list-of-posts sub-view -->
  <xsl:template match="post">
    <div class="post">
      <h2><a name="p-{id}"><a href="{@href}"><xsl:value-of select="title" /></a></a></h2>
      <span class="date"><xsl:value-of select="my:date(created)" /> 
	&#8212; <xsl:value-of select="comments"/> comment<xsl:if test="comments != 1">s</xsl:if>
      </span>
      <p><xsl:apply-templates select="body/node()" mode="html-to-xhtml" /></p>
    </div>
  </xsl:template>

  <!-- The list-of-comments sub-view -->
  <xsl:template match="comment">
    <div class="comment">
      <p>
	<a name="c-{id}" class="date"><xsl:value-of select="my:date(created)" /></a><br/>
	<xsl:apply-templates select="body/node()" mode="html-to-xhtml" />
      </p>
    </div>
  </xsl:template>

  <!-- The error message sub-view -->
  <xsl:template match="error">
    <p class="error">Error submitting comment: <xsl:value-of select="text()" /></p>
  </xsl:template>

  <xsl:template name="footer">
    <p id="footer" class="clearfix">
      The layout of this blog is a rip-off of <a href="http://www.cameronmoll.com/">Cameron Moll's</a> 
      case study <q>Tuscany Luxury Resorts</q> from Andy Budd's excellent book
      <a href="http://www.cssmastery.com/">CSS Mastery</a>.
    </p>
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

  <!-- my:truncate truncates a string with horizontal ellipsis, if too long -->
  <xsl:function name="my:truncate">
    <xsl:param name="text" />
    <xsl:param name="max-len" />
    <xsl:variable name="str" select="normalize-space(string-join($text, ''))" />
    <xsl:choose>
      <xsl:when test="string-length($str) >= $max-len">
	<xsl:value-of select="substring($str, 1, $max-len - 1)" />
	<xsl:text> &#8230;</xsl:text>
      </xsl:when>
      <xsl:otherwise>
	<xsl:value-of select="$str" />
      </xsl:otherwise>
    </xsl:choose>
  </xsl:function>

  <!-- my:date formats a date as "Day, Month NNth, YYYY" -->
  <xsl:function name="my:date">
    <xsl:param name="date" />
    <xsl:variable name="dt" select="xs:dateTime(translate($date, ' ', 'T'))" />
    <xsl:value-of select="format-dateTime($dt, '[FNn], [MNn] [D1o], [Y], [h].[m01] [Pn]', 'en', (), ())" />
  </xsl:function>

</xsl:stylesheet>
