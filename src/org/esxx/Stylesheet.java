/*
     ESXX - The friendly ECMAscript/XML Application Server
     Copyright (C) 2007-2008 Martin Blom <martin@blom.org>

     This program is free software: you can redistribute it and/or
     modify it under the terms of the GNU General Public License
     as published by the Free Software Foundation, either version 3
     of the License, or (at your option) any later version.

     This program is distributed in the hope that it will be useful,
     but WITHOUT ANY WARRANTY; without even the implied warranty of
     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
     GNU General Public License for more details.

     You should have received a copy of the GNU General Public License
     along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.esxx;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.logging.Level;
import javax.xml.transform.*;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.*;
import org.esxx.ESXX;
import org.esxx.util.URIResolver;

public class Stylesheet 
  implements org.esxx.cache.Cached {
  public Stylesheet(final URI uri, InputStream is) 
    throws IOException {
    esxx = ESXX.getInstance();

    if (is == null) {
      is = esxx.openCachedURI(uri);
    }

    this.uri = uri;
    started  = new Date();

    externalURIs.add(uri);

    XsltCompiler compiler = esxx.getSaxonProcessor().newXsltCompiler();

    URIResolver ur = new URIResolver(esxx, uri, externalURIs);

    final TransformerException[] cause = { null };

    try {
      compiler.setURIResolver(ur);
      compiler.setErrorListener(new ErrorListener() {
	  public void error(TransformerException ex)
	    throws TransformerException {
	    cause[0] = ex;
	    // 	    esxx.getLogger().logp(Level.SEVERE, uri.toString(), null,
	    // 				  ex.getMessageAndLocation(), ex);
	    throw ex;
	  }

	  public void fatalError(TransformerException ex)
	    throws TransformerException {
	    cause[0] = ex;
	    // 	    esxx.getLogger().logp(Level.SEVERE, uri.toString(), null,
	    // 				  ex.getMessage(), ex);
	    throw ex;
	  }

	  public void warning(TransformerException ex) {
	    esxx.getLogger().logp(Level.WARNING, uri.toString(), null,
				  ex.getMessageAndLocation());
	  }
	});

      xslt = compiler.compile(new StreamSource(is, uri.toString()));
    }
    catch (net.sf.saxon.s9api.SaxonApiException ex) {
      String system_id = null;
      int line = 0;
      int column = 0;

      if (cause[0] != null) {
	if (cause[0].getLocator() != null) {
	  javax.xml.transform.SourceLocator loc = cause[0].getLocator();
	  system_id = loc.getSystemId();
	  line      = loc.getLineNumber();
	  column    = loc.getColumnNumber();
	}
	else if (cause[0].getCause() instanceof org.xml.sax.SAXParseException) {
	  org.xml.sax.SAXParseException sp = (org.xml.sax.SAXParseException) cause[0].getCause();
	  system_id = sp.getSystemId();
	  line      = sp.getLineNumber();
	  column    = sp.getColumnNumber();
	}
      }

      if (system_id == null) {
	system_id = "<no system ID>";
      }

      throw new ESXXException(ex.getMessage() + ": " + system_id
			      + ", line " + line
			      + ", column " + column + ": " + cause[0].getMessage(),
			      ex);
    }
    finally {
      ur.closeAllStreams();
    }
  }

  @Override /* Cached */ public String getFilename() {
    return uri.toString();
  }

  public XsltExecutable getExecutable() {
    return xslt;
  }

  @Override /* Cached */ public synchronized void logUsage(long start_time) {
    ++invocations;
    lastAccessed  = System.currentTimeMillis();

    if (start_time != 0) {
      executionTime += (lastAccessed - start_time);
    }
  }


  @Override /* Cached */  public Collection<URI> getExternalURIs() {
    return externalURIs;
  }


  @Override public String toString() {
    return "[" + this.getClass().getName() + ": " + uri + "]";
  }

  @Override /* Cached */  public synchronized JMXBean getJMXBean() {
    if (jmxBean == null) {
      jmxBean = new JMXBean();
    }

    return jmxBean;
  }


  private class JMXBean 
    extends javax.management.StandardEmitterMBean
    implements org.esxx.jmx.StylesheetMXBean {

    public JMXBean() {
      super(org.esxx.jmx.StylesheetMXBean.class, true,
	    new javax.management.NotificationBroadcasterSupport());
    }

    @Override public String getFilename() {
      return Stylesheet.this.getFilename();
    }

    @Override public void unloadStylesheet() {
      esxx.removeCachedStylesheet(Stylesheet.this);
    }

    @Override public org.esxx.jmx.ApplicationStats getStatistics() {
      synchronized (Stylesheet.this) {
	return new org.esxx.jmx.ApplicationStats(invocations, executionTime, 
						 started, new Date(lastAccessed));
      }
    }
  }

  private ESXX esxx;
  private JMXBean jmxBean;
  private URI uri;
  private XsltExecutable xslt;
  private HashSet<URI> externalURIs = new HashSet<URI>();

  private long invocations;
  private long executionTime;
  private Date started;
  private long lastAccessed;
}
