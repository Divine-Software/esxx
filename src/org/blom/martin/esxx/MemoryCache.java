/*
     ESXX - The friendly ECMAscript/XML Application Server
     Copyright (C) 2007 Martin Blom <martin@blom.org>
     
     This program is free software; you can redistribute it and/or
     modify it under the terms of the GNU General Public License
     as published by the Free Software Foundation; either version 2
     of the License, or (at your option) any later version.
     
     This program is distributed in the hope that it will be useful,
     but WITHOUT ANY WARRANTY; without even the implied warranty of
     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
     GNU General Public License for more details.
     
     You should have received a copy of the GNU General Public License
     along with this program; if not, write to the Free Software
     Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
*/

package org.blom.martin.esxx;

import java.io.*;
import java.net.URL;
import java.util.HashMap;
import javax.xml.stream.XMLStreamException;

public class MemoryCache
  extends CacheBase {

    public MemoryCache(ESXX esxx, int max_entries, long max_size, long max_age) {
      super(esxx, max_entries, max_size, max_age);
//      System.err.println("Created memory cache");
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

    public ESXXParser getCachedESXXParser(URL url)
      throws XMLStreamException, IOException {

      String url_string = url.toString();
      ESXXParser ep;

      synchronized (cachedESXXParsers) {
	ep = cachedESXXParsers.get(url_string);
      }

      if (ep == null || checkParserURLs(url, ep)) {
	ep = new ESXXParser(esxx, url);

//	System.err.println("Created new ESXXParser " + ep);

	synchronized (cachedESXXParsers) {
	  cachedESXXParsers.put(url_string, ep);
	}
      }

      return ep;
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

    private boolean checkParserURLs(URL url, ESXXParser ep)
      throws IOException {
      if (checkURL(url)) {
	return true;
      }

      for (URL u : ep.getExternalURLs()) {
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

    private HashMap<String, ESXXParser> cachedESXXParsers = new HashMap<String, ESXXParser>();
}
