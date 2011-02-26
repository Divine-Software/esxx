<?xml version="1.0" encoding="utf-8" ?>

<xsl:stylesheet version="2.0" 
                xmlns="http://www.w3.org/1999/xhtml" 
		xmlns:atom="http://www.w3.org/2005/Atom"
		xmlns:my="urn:x-my"
		xmlns:x="http://www.w3.org/1999/xhtml"
		xmlns:xs="http://www.w3.org/2001/XMLSchema"
		xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
                exclude-result-prefixes="x atom xs my">
  <xsl:output
    doctype-public="-//W3C//DTD XHTML 1.1//EN"
    doctype-system="http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd"
    method="xhtml" version="1.0" media-type="text/html" indent="no"/>

  <xsl:variable name="menu">
    <ul>
      <li><a href="http://developer.berlios.de/projects/esxx/">BerliOS</a></li>
      <li><a href="http://openfacts2.berlios.de/wikien/index.php/BerliosProject:ESXX">Wiki</a></li>
      <li><a href="http://openfacts2.berlios.de/wikien/index.php/BerliosProject:ESXX_-_Tutorials">Tutorials</a></li>
      <li><a href="http://developer.berlios.de/bugs/?group_id=9645">Bug tracker</a></li>
      <li><a href="mailto:martin@blom.org?subject=ESXX%20Support%20Request">Get help</a></li>
    </ul>
  </xsl:variable>

  <!-- The identity transform, except that PIs and comments are stripped -->
  <xsl:template match="element()">
    <xsl:copy>
      <xsl:apply-templates select="@*,node()"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="attribute()|text()">
    <xsl:copy/>
  </xsl:template>

  <!-- Example XML:
       <error>
	 <title>ESXX Server Error</title>
	 <subtitle>File not found</subtitle>
	 <message>Error message</message>
	 <stacktrace>Stack trace ...</stacktrace>
       </error> 
  -->

  <xsl:template match="/error">
    <xsl:variable name="view">
      <view>
	<comment xml:space="preserve">
<xsl:value-of select="title" />: <xsl:value-of select="subtitle" />

<xsl:value-of select="message" />

<xsl:if test="stacktrace/text()"><xsl:value-of select="stacktrace"/></xsl:if></comment>

	<post title="{title}" date="{current-dateTime()}">
	  <h3><xsl:value-of select="subtitle" /></h3>
	  <p><xsl:value-of select="message" /></p>
	  <xsl:if test="stacktrace/text()">
	    <h3>Stack trace</h3>
	    <pre class="stacktrace"><xsl:value-of select="stacktrace" /></pre>
	  </xsl:if>
	</post>

	<sidebar>
	  <ul>
	    <li>
	      <h2>Need fix?</h2>
	      <p>Do you suspect this error is because of a bug in ESXX? If so, please accept our apologies and make sure you're using the <a href="http://developer.berlios.de/project/showfiles.php?group_id=9645">latest stable version</a>.</p>
	    </li>

	    <li>
	      <h2>Helpful resources</h2>
	      <ul>
		<li><a href="http://openfacts2.berlios.de/wikien/index.php/BerliosProject:ESXX_-_Applications">Creating ESXX applications</a></li>
		
		<li><a href="http://openfacts2.berlios.de/wikien/index.php/BerliosProject:ESXX_-_The_runtime">ESXX Runtime Reference</a></li>
		
		<li>
		  <a href="http://developer.mozilla.org/en/docs/Core_JavaScript_1.5_Guide">JavaScript 1.5 Guide</a>,
		  <a href="http://developer.mozilla.org/en/docs/Core_JavaScript_1.5_Reference">Reference</a>
		</li>
		
		<li>
		  <a href="http://developer.mozilla.org/en/New_in_JavaScript_1.6">New in JavaScript 1.6</a>, 
		  <a href="http://developer.mozilla.org/en/New_in_JavaScript_1.7">1.7</a>,
		  <a href="http://developer.mozilla.org/en/New_in_JavaScript_1.8">1.8</a>
		</li>
		
		<li><a href="https://developer.mozilla.org/En/Core_JavaScript_1.5_Guide/Processing_XML_with_E4X">Processing XML with E4x</a></li>
		
		<li><a href="http://dispatchevent.org/roger/as3-e4x-rundown/">AS3 E4X Rundown</a></li>
		
		<li><a href="http://java.sun.com/javase/6/docs/api/">Java SE 6.0 API Specification</a></li>
		
		<li>
		  <a href="http://www.h2database.com/html/grammar.html">SQL Grammar</a>,
		  <a href="http://www.h2database.com/html/functions.html">Functions</a>,
		  <a href="http://www.h2database.com/html/datatypes.html">Data&#160;Types</a>
		</li>
		
		<li>
		  <a href="http://www.w3.org/TR/xslt20/">XSLT 2.0</a>, 
		  <a href="http://www.w3.org/TR/xpath-functions/">XPath 2.0 Functions</a>
		</li>
	      </ul>
	    </li>
	  </ul>
	</sidebar>
      </view>
    </xsl:variable>

    <xsl:apply-templates select="$view" />
  </xsl:template>


  <!-- Example XML:
       <directory requestURI="/">
	 <file>...</file>
	 <directory>....</directory>
       </directory> 
  -->

  <xsl:template match="/directory">
    <xsl:variable name="view">
      <view>
	<post title="Directory Listing of '{@requestURI}'" date="{current-dateTime()}">
	  <table width="100%">
	    <thead>
	      <tr>
		<td>Name</td>
		<td>Last Modified</td>
		<td>Size</td>
		<td>Type</td>
	      </tr>
	    </thead>
	    <tbody>
	      <xsl:if test="@requestURI != '/'">
		<tr>
		  <td><a href='..'>Parent Directory</a></td>
		  <td>&#160;</td>
		  <td>&#160;</td>
		  <td>&#160;</td>
		</tr>
	      </xsl:if>

	      <xsl:apply-templates select="directory">
		<xsl:sort select="name" />
	      </xsl:apply-templates>

	      <xsl:apply-templates select="file">
		<xsl:sort select="name" />
	      </xsl:apply-templates>
	    </tbody>
	  </table>
	</post>
      </view>
    </xsl:variable>

    <xsl:apply-templates select="$view" />
  </xsl:template>

  <xsl:template match="directory/directory">
    <xsl:if test="hidden = 'false'">
      <tr>
	<td><a href="{../@requestURI}{name}/"><xsl:value-of select="name" />/</a></td>
	<td><xsl:value-of select="my:format-java-date(lastModified)" /></td>
	<td>&#160;</td>
	<td>Directory</td>
      </tr>
    </xsl:if>
  </xsl:template>

  <xsl:template match="directory/file">
    <xsl:if test="hidden = 'false'">
      <tr>
	<td><a href="{../@requestURI}{name}"><xsl:value-of select="name" /></a></td>
	<td><xsl:value-of select="my:format-java-date(lastModified)" /></td>
	<td><xsl:value-of select="length" /></td>
	<td><xsl:value-of select="type" /></td>
      </tr>
    </xsl:if>
  </xsl:template>

  <xsl:template match="x:view">
    <xsl:if test="x:comment">
      <xsl:comment><xsl:value-of select="x:comment" /></xsl:comment>
    </xsl:if>

    <html>
      <head>
	<title>ESXX &#8212; <xsl:value-of select="x:post/@title" /></title>
	<meta name="keywords" content="ssjs, server-side javascript, ecmascript, xml, application server"/>
	<link href="mailto:martin@blom.org" rev="made" />
	<link href='http://esxx.org/favicon.ico' rel='shortcut icon' type='image/vnd.microsoft.icon'/>
	<link href="?!esxx-rsrc=esxx.css" rel="stylesheet" type="text/css" media="screen" />
      </head>
      <body>
	<div id="bg1">
	  <div id="header">
	    <h1><a href="http://esxx.org/">ESXX<sup>@VERSION@ &#946;</sup></a></h1>
	    <h2>A Divine Software&#8482; production</h2>
	  </div>
	</div>

	<div id="bg2">
	  <div id="header2">
	    <div id="menu">
	      <xsl:apply-templates select="$menu/*" />
	    </div>
	    
	    <div id="search">
	      <form action="http://www.google.com/cse" id="cse-search-box">
		<fieldset>
		  <input type="hidden" name="cx" value="partner-pub-2115989180089866:ayx3ndbp8fl" />
		  <input type="hidden" name="ie" value="UTF-8" />
		  <input type="text" name="q" class="text"/>
		  <input type="submit" name="sa" value="Search" class="button"/>
		</fieldset>
	      </form>
	      <script type="text/javascript"><![CDATA[
(function() {
  var f = document.getElementById("cse-search-box");
  if (f) if (f.q) {
    f.q.onblur  = function() { f.q.value = "Search site, blog, wiki"; };
    f.q.onfocus = function() { f.q.value = ""; };
    f.q.onblur();
  }
})();
]]></script>
	    </div>
	  </div>
	</div>

	<div id="bg3">
	  <div id="bg4">
	    <div id="bg5">
	      <div id="page">
		<div id="sidebar">
		  <xsl:apply-templates select="x:sidebar/*" />
		</div>

		<div id="content">
		  <xsl:apply-templates select="x:post" />
		</div>

		<div id="donations">
		  <xsl:call-template name="donations" />
		</div>
	      </div>
	    </div>
	  </div>
	</div>

	<div id="footer">
	  <p>2007-<xsl:value-of select="year-from-date(current-date())"/>&#160;<a href="mailto:martin@blom.org?subject=ESXX">Martin Blom</a>, 
	    Divine Software. Design by 
	    <a href="http://www.nodethirtythree.com/">nodeThirtyThree</a> + 
	    <a href="http://www.freecsstemplates.org/">Free CSS Templates</a> (130&#176;, 60%).
	  </p>
	</div>
      </body>
    </html>
  </xsl:template>


  <xsl:template match="x:post">
    <div class="post">
      <div class="title">
	<h2><xsl:value-of select="@title" /></h2>
<!--	<p><xsl:value-of select="my:date(@date)" /></p> -->
      </div>

      <div class="entry">
	<xsl:apply-templates select="*" />
      </div>
    </div>
  </xsl:template>

  <!-- Donation table -->
  <xsl:template name="donations">
<table summary="Donation options">
<caption>Encourage further development by making a donation!</caption>
<thead><tr>
<td>&#8364; 10</td>
<td>&#8364; 50</td>
<td>&#8364; 100</td>
<td>&#8364; 500</td>
<td>&#8364; 1000</td>
<td>&#8364; 5000</td>
</tr></thead>
<tbody><tr>
<td>
<form action="https://www.paypal.com/cgi-bin/webscr" method="post"><div>
<input type="hidden" name="cmd" value="_xclick" />
<input type="hidden" name="business" value="martin@blom.org" />
<input type="hidden" name="item_name" value="ESXX" />
<input type="hidden" name="amount" value="8" />
<input type="hidden" name="no_shipping" value="1" />
<input type="hidden" name="logo_custom" value="https://www.paypal.com/en_US/i/btn/btn_donateCC_LG.gif" />
<input type="hidden" name="no_note" value="1" />
<input type="hidden" name="currency_code" value="EUR" />
<input type="hidden" name="tax" value="2" />
<input type="hidden" name="lc" value="US" />
<input type="hidden" name="bn" value="PP-BuyNowBF" />
<input type="image" src="https://www.paypal.com/en_US/i/btn/btn_donateCC_LG.gif" name="submit" alt="PayPal - The safer, easier way to pay online!" />
<img alt="" src="https://www.paypal.com/en_US/i/scr/pixel.gif" width="1" height="1" />
</div></form>
</td>
<td>
<form action="https://www.paypal.com/cgi-bin/webscr" method="post"><div>
<input type="hidden" name="cmd" value="_xclick" />
<input type="hidden" name="business" value="martin@blom.org" />
<input type="hidden" name="item_name" value="ESXX" />
<input type="hidden" name="amount" value="40" />
<input type="hidden" name="no_shipping" value="1" />
<input type="hidden" name="logo_custom" value="https://www.paypal.com/en_US/i/btn/btn_donateCC_LG.gif" />
<input type="hidden" name="no_note" value="1" />
<input type="hidden" name="currency_code" value="EUR" />
<input type="hidden" name="tax" value="10" />
<input type="hidden" name="lc" value="US" />
<input type="hidden" name="bn" value="PP-BuyNowBF" />
<input type="image" src="https://www.paypal.com/en_US/i/btn/btn_donateCC_LG.gif" name="submit" alt="PayPal - The safer, easier way to pay online!" />
<img alt="" src="https://www.paypal.com/en_US/i/scr/pixel.gif" width="1" height="1" />
</div></form>
</td>
<td>
<form action="https://www.paypal.com/cgi-bin/webscr" method="post"><div>
<input type="hidden" name="cmd" value="_xclick" />
<input type="hidden" name="business" value="martin@blom.org" />
<input type="hidden" name="item_name" value="ESXX" />
<input type="hidden" name="amount" value="80" />
<input type="hidden" name="no_shipping" value="1" />
<input type="hidden" name="logo_custom" value="https://www.paypal.com/en_US/i/btn/btn_donateCC_LG.gif" />
<input type="hidden" name="no_note" value="1" />
<input type="hidden" name="currency_code" value="EUR" />
<input type="hidden" name="tax" value="20" />
<input type="hidden" name="lc" value="US" />
<input type="hidden" name="bn" value="PP-BuyNowBF" />
<input type="image" src="https://www.paypal.com/en_US/i/btn/btn_donateCC_LG.gif" name="submit" alt="PayPal - The safer, easier way to pay online!" />
<img alt="" src="https://www.paypal.com/en_US/i/scr/pixel.gif" width="1" height="1" />
</div></form>
</td>
<td>
<form action="https://www.paypal.com/cgi-bin/webscr" method="post"><div>
<input type="hidden" name="cmd" value="_xclick" />
<input type="hidden" name="business" value="martin@blom.org" />
<input type="hidden" name="item_name" value="ESXX" />
<input type="hidden" name="amount" value="400" />
<input type="hidden" name="no_shipping" value="1" />
<input type="hidden" name="logo_custom" value="https://www.paypal.com/en_US/i/btn/btn_donateCC_LG.gif" />
<input type="hidden" name="no_note" value="1" />
<input type="hidden" name="currency_code" value="EUR" />
<input type="hidden" name="tax" value="100" />
<input type="hidden" name="lc" value="US" />
<input type="hidden" name="bn" value="PP-BuyNowBF" />
<input type="image" src="https://www.paypal.com/en_US/i/btn/btn_donateCC_LG.gif" name="submit" alt="PayPal - The safer, easier way to pay online!" />
<img alt="" src="https://www.paypal.com/en_US/i/scr/pixel.gif" width="1" height="1" />
</div></form>
</td>
<td>
<form action="https://www.paypal.com/cgi-bin/webscr" method="post"><div>
<input type="hidden" name="cmd" value="_xclick" />
<input type="hidden" name="business" value="martin@blom.org" />
<input type="hidden" name="item_name" value="ESXX" />
<input type="hidden" name="amount" value="800" />
<input type="hidden" name="no_shipping" value="1" />
<input type="hidden" name="logo_custom" value="https://www.paypal.com/en_US/i/btn/btn_donateCC_LG.gif" />
<input type="hidden" name="no_note" value="1" />
<input type="hidden" name="currency_code" value="EUR" />
<input type="hidden" name="tax" value="200" />
<input type="hidden" name="lc" value="US" />
<input type="hidden" name="bn" value="PP-BuyNowBF" />
<input type="image" src="https://www.paypal.com/en_US/i/btn/btn_donateCC_LG.gif" name="submit" alt="PayPal - The safer, easier way to pay online!" />
<img alt="" src="https://www.paypal.com/en_US/i/scr/pixel.gif" width="1" height="1" />
</div></form>
</td>
<td>
<form action="https://www.paypal.com/cgi-bin/webscr" method="post"><div>
<input type="hidden" name="cmd" value="_xclick" />
<input type="hidden" name="business" value="martin@blom.org" />
<input type="hidden" name="item_name" value="ESXX" />
<input type="hidden" name="amount" value="4000" />
<input type="hidden" name="no_shipping" value="1" />
<input type="hidden" name="logo_custom" value="https://www.paypal.com/en_US/i/btn/btn_donateCC_LG.gif" />
<input type="hidden" name="no_note" value="1" />
<input type="hidden" name="currency_code" value="EUR" />
<input type="hidden" name="tax" value="1000" />
<input type="hidden" name="lc" value="US" />
<input type="hidden" name="bn" value="PP-BuyNowBF" />
<input type="image" src="https://www.paypal.com/en_US/i/btn/btn_donateCC_LG.gif" name="submit" alt="PayPal - The safer, easier way to pay online!" />
<img alt="" src="https://www.paypal.com/en_US/i/scr/pixel.gif" width="1" height="1" />
</div></form>
</td>
</tr></tbody></table>
  </xsl:template>


  <!-- my:date formats a date string as "Month NNth, year" -->
  <xsl:function name="my:date">
    <xsl:param name="date" />
    <xsl:variable name="dt" select="xs:dateTime(translate($date, ' ', 'T'))" />
    <xsl:value-of select="format-dateTime($dt, '[MNn] [D1o], [Y]', 'en', (), ())" />
  </xsl:function>

  <!-- my:format-java-date formats a Java timestamp as "YYYY-MM-DD, HH:MM:SS" -->
  <xsl:function name="my:format-java-date">
    <xsl:param name="millis" />
    <xsl:variable name="dt" select="xs:dateTime('1970-01-01T00:00:00') 
				    + $millis * xs:dayTimeDuration('PT0.001S')"/>
    <xsl:value-of select="format-dateTime($dt, '[Y0001]-[M01]-[D01] [H01]:[m01]:[s01]', 
			  'en', (), ())" />
  </xsl:function>

</xsl:stylesheet>
