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

package org.esxx.js;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import org.esxx.ESXX;
import org.mozilla.javascript.*;

public class UrlURI
  extends JSURI {
    public UrlURI(URI uri) {
      super(uri);
    }

    @Override
    protected Object load(Context cx, Scriptable thisObj,
			  String type, HashMap<String,String> params)
      throws Exception {
      ESXX      esxx = ESXX.getInstance();
      URL        url = uri.toURL();
      String[]    ct = { null };
      InputStream is = esxx.openCachedURL(url, ct);

      if (type == null) {
	if (ct[0] != null) {
	  params.clear();
	  type = ESXX.parseMIMEType(ct[0], params);
	}
	else {
	  type = "text/xml";
	}
      }

      JSESXX js_esxx = JSGlobal.getJSESXX(cx, thisObj);
      Object result  = esxx.parseStream(type, params,
					is, url,
					null,
					js_esxx.jsGet_debug(),
					cx, this);

      if (result == null) {
	return super.load(cx, thisObj, type, params);
      }
      else {
	return result;
      }
    }
}
