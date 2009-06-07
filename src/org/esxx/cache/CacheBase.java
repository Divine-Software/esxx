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
import java.net.URLConnection;
import java.net.HttpURLConnection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.Inflater;

public abstract class CacheBase {
    public CacheBase(ESXX esxx, int max_entries, long max_size, long max_age) {
      this.esxx  = esxx;
      maxEntries = max_entries;
      maxSize    = max_size;
      maxAge     = max_age;
    }

    public abstract InputStream openCachedURL(URL url, String[] content_type)
      throws IOException;

    protected CachedURL getCachedURL(URL url) {
      CachedURL cached;

      synchronized (cachedURLs) {
	String url_string = url.toString();

	cached = cachedURLs.get(url_string);

	if (cached == null) {
	  cached = new CachedURL(url);
	  cachedURLs.put(url_string, cached);
	}
      }

      return cached;
    }

    protected InputStream getStreamIfModified(CachedURL cached)
      throws IOException {

      URLConnection uc = cached.url.openConnection();

      uc.setDoInput(true);
      uc.setDoOutput(false);
      uc.setIfModifiedSince(cached.lastModified);

      InputStream is;

      if (uc instanceof HttpURLConnection) {
	HttpURLConnection huc = (HttpURLConnection) uc;

	try {
	  HttpURLConnection.setFollowRedirects(true);
	}
	catch (SecurityException ex) {
	  // Probably a Google App Engine problem
	}

	huc.setRequestProperty("Accept-Encoding", "gzip, deflate");
	huc.setRequestProperty("Accept", "text/xml,text/html;q=0.9,text/plain;q=0.8,*/*;q=0.1");

	if (cached.eTag != null) {
	  huc.addRequestProperty("If-None-Match", cached.eTag);
	}

	huc.connect();

	if (huc.getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED) {
	  huc.getInputStream().close();
	  return null;
	}

	// It's modified: update protocol-specific meta-info ...
	cached.eTag = huc.getHeaderField("ETag");

	// ... and get the input stream
	String encoding = huc.getContentEncoding();

	if (encoding != null && encoding.equalsIgnoreCase("gzip")) {
	  is = new GZIPInputStream(huc.getInputStream());
	}
	else if (encoding != null && encoding.equalsIgnoreCase("deflate")) {
	  is = new InflaterInputStream(huc.getInputStream(), new Inflater(true));
	}
	else {
	  is = huc.getInputStream();
	}
      }
      else {
	uc.connect();

	if (uc.getLastModified() <= cached.lastModified) {
	  uc.getInputStream().close();
	  return null;
	}

	is = uc.getInputStream();
      }

      cached.lastChecked   = System.currentTimeMillis();

      cached.lastModified  = uc.getLastModified();
      cached.contentType   = uc.getContentType();
      cached.contentLength = uc.getContentLength();

      return is;
    }

    protected static class CachedURL {
	public CachedURL(URL url) {
	  this.url = url;

	  lastModified = 0;
	  lastChecked = 0;
	  eTag = null;

	  contentType = null;
	  contentLength = -1;
	  content = null;
	}

        @Override
	public String toString() {
	  return "CachedURL " + url + ", modified " + new java.util.Date(lastModified) +
	    ", ETag " + eTag + ", type " + contentType + ", size " + contentLength;
	}

	public URL url;

	public long lastChecked;

	public long lastModified;
	public String eTag;

	public String contentType;
	public int contentLength;
	public Object content;
    }

    private class LRUMap
      extends LinkedHashMap<String, CachedURL> {

	public LRUMap() {
	  super(128, 0.75f, true);
	}

	private boolean isFull(CachedURL eldest) {
	  return (size() > maxEntries ||
		  currentSize > maxSize ||
		  (eldest.lastChecked != 0 &&
		   eldest.lastChecked < (System.currentTimeMillis() - maxAge)));
	}

	@Override
	protected boolean removeEldestEntry(Map.Entry<String, CachedURL> eldest) {
	  if (isFull(eldest.getValue())) {

	    Iterator<Map.Entry<String, CachedURL>> i = entrySet().iterator();

	    while (i.hasNext()) {
	      Map.Entry<String, CachedURL> e = i.next();

	      if (!isFull(e.getValue())) {
		break;
	      }

	      i.remove();
	    }
	  }

	  // Tell implementation not to auto-modify the hash table
	  return false;
	}

	static final long serialVersionUID = 8565024717836226408L;
    }

    private LRUMap cachedURLs = new LRUMap();

    private int maxEntries;
    private long maxSize;
    private long maxAge;

    private long currentSize;

    protected ESXX esxx;
}