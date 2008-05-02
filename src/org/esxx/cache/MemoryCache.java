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

package org.esxx.cache;

import org.esxx.*;

import java.io.*;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import net.sf.saxon.s9api.*;
import javax.xml.transform.stream.StreamSource;

public class MemoryCache
  extends CacheBase {

    public MemoryCache(ESXX esxx, int max_entries, long max_size, long max_age) {
      super(esxx, max_entries, max_size, max_age);
    }

    public InputStream openCachedURL(URL url, String[] content_type) 
      throws IOException {
      CacheBase.CachedURL cached = getCachedURL(url);

      synchronized (cached) {
	updateCachedURL(cached);

	if (content_type != null) {
	  content_type[0] = cached.contentType;
	}

	return new ByteArrayInputStream((byte[]) cached.content);
      }
    }

    public Application getCachedApplication(URL url)
      throws IOException {
      String url_string = url.toString();
      Application app;

      synchronized (cachedApplications) {
	app = cachedApplications.get(url_string);
      }

      if (app == null || checkApplicationURLs(url, app)) {
	app = new Application(esxx, url);

	synchronized (cachedApplications) {
	  cachedApplications.put(url_string, app);
	}
      }

      return app;
    }

    public XsltExecutable getCachedStylesheet(URL url, PrintWriter err)
      throws IOException {
      try {
	if (url == null) {
	  return esxx.compileStylesheet(null, null, null, null);
	}

	String url_string = url.toString();;
	Stylesheet xslt;

	synchronized (cachedStylesheets) {
	  xslt = cachedStylesheets.get(url_string);
	}

	if (xslt == null || checkStylesheetURLs(url, xslt)) {
	  xslt = new Stylesheet();
	  xslt.xsltExecutable = esxx.compileStylesheet(esxx.openCachedURL(url), url, 
						       xslt.externalURLs, err);
	}

	if (xslt == null) {
	  synchronized (cachedStylesheets) {
	    cachedStylesheets.put(url_string, xslt);
	  }
	}

	return xslt.xsltExecutable;
      }
      catch (SaxonApiException ex) {
	throw new IOException("Failed to compile XSLT stylesheet: " + ex.getMessage(), ex);
      }
    }

    private boolean updateCachedURL(CacheBase.CachedURL cached)
      throws IOException {
      InputStream is = getStreamIfModified(cached);

      if (is != null) {
	// URL is modified
	ByteArrayOutputStream os = new ByteArrayOutputStream();
	
//	System.err.println("Reloading modified URL " + cached);

	esxx.copyStream(is, os);
	cached.content = os.toByteArray();
	return true;
      }
      else {
	return false;
      }
    }

    private boolean checkApplicationURLs(URL url, Application app)
      throws IOException {
      if (checkURL(url)) {
	return true;
      }

      for (URL u : app.getExternalURLs()) {
	if (checkURL(u)) {
	  return true;
	}
      }

      return false;
    }

    private boolean checkStylesheetURLs(URL url, Stylesheet xslt)
      throws IOException {
      if (checkURL(url)) {
	return true;
      }

      for (URL u : xslt.externalURLs) {
	if (checkURL(u)) {
	  return true;
	}
      }

      return false;
    }

    private boolean checkURL(URL url)
      throws IOException {
      CacheBase.CachedURL cached = getCachedURL(url);
	
      synchronized (cached) {
//	System.err.println("Checking URL " + url);
	return updateCachedURL(cached);
      }
    }

    private class Stylesheet {
      HashSet<URL> externalURLs = new HashSet<URL>();
      XsltExecutable xsltExecutable;
    }

    private HashMap<String, Application> cachedApplications = new HashMap<String, Application>();
    private HashMap<String, Stylesheet> cachedStylesheets = new HashMap<String, Stylesheet>();
}
