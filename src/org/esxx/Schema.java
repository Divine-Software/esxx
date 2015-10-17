/*
     ESXX - The friendly ECMAscript/XML Application Server
     Copyright (C) 2007-2015 Martin Blom <martin@blom.org>

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

import com.thaiopensource.util.PropertyMap;
import com.thaiopensource.util.PropertyMapBuilder;
import com.thaiopensource.validate.IncorrectSchemaException;
import com.thaiopensource.validate.SchemaReader;
import com.thaiopensource.validate.ValidateProperty;
import com.thaiopensource.validate.Validator;
import com.thaiopensource.validate.auto.AutoSchemaReader;
import com.thaiopensource.validate.rng.CompactSchemaReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import org.esxx.util.URIResolver;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ErrorHandler;


 import java.util.logging.Level;
// import javax.xml.transform.*;
// import javax.xml.transform.stream.StreamSource;
// import net.sf.saxon.s9api.*;
// import org.esxx.ESXX;
// import org.esxx.util.URIResolver;

public class Schema 
  implements org.esxx.cache.Cached {
  public Schema(final URI uri, InputStream is, String type) 
    throws IOException {
    esxx = ESXX.getInstance();

    if (is == null) {
      is = esxx.openCachedURI(uri);
    }

    this.uri = uri;
    started  = new Date();

    externalURIs.add(uri);

    try {
      PropertyMapBuilder pmb = new PropertyMapBuilder();
      URIResolver        res = new URIResolver(esxx, uri, externalURIs);
      ErrorHandler       eh  = new ErrorHandler() {
    	  public void error(SAXParseException ex)
    	    throws SAXParseException {
    	    throw ex;
    	  }

    	  public void fatalError(SAXParseException ex)
    	    throws SAXParseException {
    	    throw ex;
    	  }

    	  public void warning(SAXParseException ex) {
    	    esxx.getLogger().logp(Level.WARNING, uri.toString(), null,
    				  "Line " + ex.getLineNumber() + ", column " + ex.getColumnNumber()
				  + ": " + ex.getMessage());
    	  }
	};

      pmb.put(ValidateProperty.ENTITY_RESOLVER, res);
      pmb.put(ValidateProperty.ERROR_HANDLER, eh);
      pmb.put(ValidateProperty.URI_RESOLVER, res);
      propertyMap = pmb.toPropertyMap();

      SchemaReader sr = ("application/relax-ng-compact-syntax".matches(type) ||
			 "application/x-rnc".matches(type) ?
			 CompactSchemaReader.getInstance() : new AutoSchemaReader());
      schema = sr.createSchema(new InputSource(is), propertyMap);
    }
    catch (SAXException ex) {
      throw new IOException("Failed to load schema: " + ex.getMessage(), ex);
    }
    catch (IncorrectSchemaException ex) {
      throw new IOException("Failed to load schema: " + ex.getMessage(), ex);
    }
  }

  @Override public String toString() {
    return "[" + this.getClass().getName() + ": " + uri + "]";
  }

  @Override /* Cached */ public String getFilename() {
    return uri.toString();
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

  @Override /* Cached */  public synchronized JMXBean getJMXBean() {
    if (jmxBean == null) {
      jmxBean = new JMXBean();
    }

    return jmxBean;
  }

  public void validate(Node node) {
    Validator      v = schema.createValidator(propertyMap);
    XMLReader      r = new gnu.xml.util.DomParser(node);
    ContentHandler h = v.getContentHandler();

    r.setContentHandler(h);

    if (v.getDTDHandler() != null) {
      r.setDTDHandler(v.getDTDHandler());
    }

    try {
      if (node.getNodeType() != Node.DOCUMENT_NODE) {
	h.startDocument();
      }

      r.parse("ignored");

      if (node.getNodeType() != Node.DOCUMENT_NODE) {
	h.endDocument();
      }
    }
    catch (SAXParseException ex) {
      throw new ESXXException(422 /* Unprocessable Entity */, ex.getMessage(), ex);
    }
    catch (IOException ex) {
      throw new ESXXException("IOException: " + ex.getMessage(), ex);
    }
    catch (SAXException ex) {
      throw new ESXXException("SAXException: " + ex.getMessage(), ex);
    }
  }

  private class JMXBean 
    extends javax.management.StandardEmitterMBean
    implements org.esxx.jmx.SchemaMXBean {

    public JMXBean() {
      super(org.esxx.jmx.SchemaMXBean.class, true,
  	    new javax.management.NotificationBroadcasterSupport());
    }

    @Override public String getFilename() {
      return Schema.this.getFilename();
    }

    @Override public void unloadSchema() {
      esxx.removeCachedSchema(Schema.this);
    }

    @Override public org.esxx.jmx.ApplicationStats getStatistics() {
      synchronized (Schema.this) {
  	return new org.esxx.jmx.ApplicationStats(invocations, executionTime, 
  						 started, new Date(lastAccessed));
      }
    }
  }

  private ESXX esxx;
  private JMXBean jmxBean;
  private URI uri;
  private com.thaiopensource.validate.Schema schema;
  private PropertyMap propertyMap;
  private HashSet<URI> externalURIs = new HashSet<URI>();

  private long invocations;
  private long executionTime;
  private Date started;
  private long lastAccessed;
}
