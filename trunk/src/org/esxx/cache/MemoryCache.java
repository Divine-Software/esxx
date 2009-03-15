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

import org.esxx.ESXX;
import org.esxx.util.IO;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class MemoryCache
  extends CacheBase {

  public MemoryCache(ESXX esxx, int max_entries, long max_size, long max_age) {
    super(esxx, max_entries, max_size, max_age);
  }

  @Override public InputStream openCachedURL(URL url, String[] content_type)
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


  private boolean updateCachedURL(CacheBase.CachedURL cached)
    throws IOException {
    InputStream is = getStreamIfModified(cached);

    if (is != null) {
      // URL is modified
      ByteArrayOutputStream os = new ByteArrayOutputStream();

      //	System.err.println("Reloading modified URL " + cached);

      IO.copyStream(is, os);
      cached.content = os.toByteArray();
      is.close();
      return true;
    }
    else {
      return false;
    }
  }
}
