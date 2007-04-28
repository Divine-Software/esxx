
package org.blom.martin.esxx;

import java.io.*;
import java.net.URL;
import java.util.HashMap;
import javax.xml.stream.XMLStreamException;

public class MemoryCache
  extends CacheBase {

    public MemoryCache(ESXX esxx) {
      super(esxx);
    }

    public InputStream openCachedURL(URL url) 
      throws IOException {
      CacheBase.CachedURL cached = getCachedURL(url);

      System.err.println("Checking " + cached);
      InputStream is = getStreamIfModified(cached);

      if (is != null) {
	System.err.println("Reloading " + cached);

	// URL is modified
	ByteArrayOutputStream os = new ByteArrayOutputStream();
	
	copy(is, os);
	cached.content = os.toByteArray();
      }

      return new ByteArrayInputStream((byte[]) cached.content);
    }

    public ESXXParser getCachedESXXParser(URL url)
      throws XMLStreamException, IOException {

      return new ESXXParser(esxx, url);
    }
}
