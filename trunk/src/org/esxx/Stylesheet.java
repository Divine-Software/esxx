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
  extends javax.management.StandardEmitterMBean
  implements org.esxx.jmx.StylesheetMXBean {
  public Stylesheet(final URI uri) 
    throws IOException {

    super(org.esxx.jmx.StylesheetMXBean.class, true, 
	  new javax.management.NotificationBroadcasterSupport());

    esxx = ESXX.getInstance();
    this.uri = uri;
    started  = new Date();

    externalURIs.add(uri);

    XsltCompiler compiler = esxx.getSaxonProcessor().newXsltCompiler();

    URIResolver ur = new URIResolver(esxx, externalURIs);

    try {
      compiler.setURIResolver(ur);
      compiler.setErrorListener(new ErrorListener() {
	  public void error(TransformerException ex)
	    throws TransformerException {
	    esxx.getLogger().logp(Level.SEVERE, uri.toString(), null,
				 ex.getMessageAndLocation(), ex);
	    throw ex;
	  }

	  public void fatalError(TransformerException ex)
	    throws TransformerException {
	    esxx.getLogger().logp(Level.SEVERE, uri.toString(), null,
				 ex.getMessageAndLocation(), ex);
	    throw ex;
	  }

	  public void warning(TransformerException ex) {
	    esxx.getLogger().logp(Level.WARNING, uri.toString(), null,
				 ex.getMessageAndLocation());
	  }
	});

      xslt = compiler.compile(new StreamSource(esxx.openCachedURI(uri), uri.toString()));
    }
    catch (net.sf.saxon.s9api.SaxonApiException ex) {
      throw new ESXXException(ex.getMessage(), ex);
    }
    finally {
      ur.closeAllStreams();
    }
  }

  @Override public String getFilename() {
    return uri.toString();
  }

  public XsltExecutable getExecutable() {
    return xslt;
  }

  public synchronized void logUsage(long start_time) {
    ++invocations;
    lastAccessed  = System.currentTimeMillis();

    if (start_time != 0) {
      executionTime += (lastAccessed - start_time);
    }
  }

  @Override public synchronized org.esxx.jmx.ApplicationStats getStatistics() {
    return new org.esxx.jmx.ApplicationStats(invocations, executionTime, 
					     started, new Date(lastAccessed));
  }


  public Collection<URI> getExternalURIs() {
    return externalURIs;
  }

  @Override public void unloadStylesheet() {
    esxx.removeCachedStylesheet(this);
  }

  @Override public String toString() {
    return "[" + this.getClass().getName() + ": " + uri + "]";
  }

  private ESXX esxx;
  private URI uri;
  private XsltExecutable xslt;
  private HashSet<URI> externalURIs = new HashSet<URI>();

  private long invocations;
  private long executionTime;
  private Date started;
  private long lastAccessed;
}
