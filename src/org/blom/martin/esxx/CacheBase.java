
package org.blom.martin.esxx;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.Inflater;
import javax.xml.stream.XMLStreamException;

public abstract class CacheBase {
    public CacheBase(ESXX esxx) {
      this.esxx = esxx;
    }

    public abstract InputStream openCachedURL(URL url) 
      throws IOException;

    public abstract ESXXParser getCachedESXXParser(URL url) 
      throws XMLStreamException, IOException;

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

      synchronized (cached) {
	URLConnection uc = cached.url.openConnection();

	uc.setIfModifiedSince(cached.lastModified);

	InputStream is;

	if (uc instanceof HttpURLConnection) {
	  HttpURLConnection huc = (HttpURLConnection) uc;

	  huc.setFollowRedirects(true);
	  huc.setRequestProperty("Accept-Encoding", "gzip, deflate");

	  if (cached.eTag != null) {
	    huc.addRequestProperty("If-None-Match", cached.eTag);
	  }

	  huc.connect();

	  if (huc.getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED) {
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
    }

    protected static void copy(InputStream is, OutputStream os) 
      throws IOException {
      byte buffer[] = new byte[8192];
               
      int bytesRead;
               
      while ((bytesRead = is.read(buffer)) != -1) {
	os.write(buffer, 0, bytesRead);
      }
               
      os.flush();
      os.close();
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

    protected HashMap<String, CachedURL> cachedURLs = new HashMap<String, CachedURL>();

    protected ESXX esxx;
}