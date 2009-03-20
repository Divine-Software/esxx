
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
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

      <body>
      </body>
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
	      <h1>The Ajax Blog</h1>
	      <p><a href="{$adminURI}">Administration</a></p>
	    </div>
	  
	    <div id="navcontainer">
	      <ul id="nav">
		<xsl:for-each select="posts/post[7 >= position()]">
		  <li>
		    <span><xsl:number format="I"/></span>
		    <a href="#{id}"><xsl:value-of select="title" /></a>
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
      </body>
    </html>
  </xsl:template>

  <!-- The Blog post view -->
  <xsl:template match="/blog-entry">
    <html>
      <head>
	<title>The Ajax Blog: <xsl:value-of select="post/title"/></title>
      </head>
      <body>
	  <div id="header">
	    <h1>The Ajax Blog</h1>
	    <p><a href="{$adminURI}">Administration</a></p>
	  </div>
	
	  <div class="posts">
	    <xsl:apply-templates select="posts/post" />
	  </div>

	  <div class="comments">
	    <h3>Comments</h3>
	    <xsl:apply-templates select="comments/comment" />
	  </div>
      </body>
    </html>
  </xsl:template>

  <!-- The list-of-posts sub-view -->
  <xsl:template match="posts/post">
    <div class="post">
      <h2><a href="{@href}"><xsl:value-of select="title" /></a></h2>
      <p><xsl:apply-templates select="body/node()" mode="html-to-xhtml" /></p>
    </div>
  </xsl:template>

  <!-- The list-of-comments sub-view -->
  <xsl:template match="comments/comment">
    <div class="comment">
      <p><xsl:apply-templates select="body/node()" mode="html-to-xhtml" /></p>
      <hr/>
    </div>
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
