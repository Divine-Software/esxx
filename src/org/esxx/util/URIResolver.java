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

package org.esxx.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.LinkedList;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import org.esxx.ESXX;
import org.w3c.dom.ls.LSException;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

public class URIResolver
  implements javax.xml.transform.URIResolver, LSResourceResolver, EntityResolver {

  public URIResolver(ESXX esxx, URI base_uri, Collection<URI> log_visited) {
    this.esxx = esxx;
    baseURI = base_uri;
    logVisited = log_visited;
    openedStreams = new LinkedList<InputStream>();
  }


  public void closeAllStreams() {
    for (InputStream is : openedStreams) {
      try { is.close(); } catch (IOException ignored) {}
    }
  }


  @Override /* URIResolver */ public Source resolve(String href,
						    String base) 
    throws TransformerException {
    try {
      URI uri = getURI(href, base);
      return new javax.xml.transform.stream.StreamSource(getIS(uri));
    }
    catch (IOException ex) {
      throw new TransformerException("Failed to resolve resource " + href + ": " + ex.getMessage(),
				     ex);
    }
  }


  @Override /* LSResourceResolver */ public LSInput resolveResource(String type,
								    String namespaceURI,
								    String publicId,
								    String systemId,
								    String baseURI) {
    try {
      LSInput lsi = esxx.getDOMImplementationLS().createLSInput();
      URI     uri = getURI(systemId, baseURI);

      lsi.setSystemId(uri.toString());
      lsi.setByteStream(getIS(uri));

      return lsi;
    }
    catch (IOException ex) {
      throw new LSException(LSException.PARSE_ERR, 
			    "Failed to resolve resource " + systemId + ": " + ex.getMessage());
    }
  }

  @Override /* EntityResolver */ public InputSource resolveEntity(String publicId, 
								  String systemId) 
    throws IOException {

    if (systemId == null) {
      throw new IOException("System ID missing");
    }

    return new InputSource(getIS(getURI(systemId, null)));
  }

  private URI getURI(String uri, String base_uri) 
    throws IOException {
    try {
      URI base = baseURI;

      if (base_uri != null) {
	base = new URI(base_uri);
      }

      return base.resolve(uri);
    }
    catch (java.net.URISyntaxException ex) {
      throw new IOException(ex.getMessage(), ex);
    }
  }


  private InputStream getIS(URI uri) 
    throws IOException {
    InputStream is = esxx.openCachedURI(uri);

    if (logVisited != null) {
      // Log visited URIs if successfully opened
      logVisited.add(uri);
    }

    openedStreams.add(is);

    return is;
  }

  private ESXX esxx;
  private URI baseURI;
  private Collection<URI> logVisited;
  private Collection<InputStream> openedStreams;
}
