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

import java.io.*;
import java.util.HashMap;
import org.esxx.ESXX;

public abstract class IO {
    public static Reader createReader(InputStream is, String content_type)
      throws java.io.UnsupportedEncodingException {
      HashMap<String, String> params = new HashMap<String, String>();
      ESXX.parseMIMEType(content_type, params);
      String cs = params.get("charset");

      if (cs == null) {
	cs = java.nio.charset.Charset.defaultCharset().name();
      }

      return new InputStreamReader(is, cs);
    }

    public static Writer createWriter(OutputStream os, String content_type)
      throws java.io.UnsupportedEncodingException {
      HashMap<String, String> params = new HashMap<String, String>();
      ESXX.parseMIMEType(content_type, params);
      String cs = params.get("charset");

      if (cs == null) {
	cs = java.nio.charset.Charset.defaultCharset().name();
      }

      return new OutputStreamWriter(os, cs);
    }

    public static void copyReader(Reader r, Writer w)
      throws IOException {
      char buffer[] = new char[8192];

      int charsRead;

      while ((charsRead = r.read(buffer)) != -1) {
	w.write(buffer, 0, charsRead);
      }

      w.flush();
    }

    public static void copyStream(InputStream is, OutputStream os)
      throws IOException {
      byte buffer[] = new byte[8192];

      int bytesRead;

      while ((bytesRead = is.read(buffer)) != -1) {
	os.write(buffer, 0, bytesRead);
      }

      os.flush();
    }
}
