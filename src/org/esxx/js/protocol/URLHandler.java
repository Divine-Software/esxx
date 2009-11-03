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

package org.esxx.js.protocol;

import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import org.esxx.*;
import org.esxx.js.*;
import org.mozilla.javascript.*;

public class URLHandler
  extends ProtocolHandler {
  public URLHandler(JSURI jsuri)
    throws URISyntaxException {
    super(jsuri);
  }

  @Override
  public Object load(Context cx, Scriptable thisObj,
		     String type, HashMap<String,String> params)
    throws Exception {
    ESXX        esxx = ESXX.getInstance();
    URL          url = jsuri.getURI().toURL();
    URLConnection uc = url.openConnection();

    uc.setDoInput(true);
    uc.setDoOutput(false);
    uc.connect();

    String      ct = uc.getContentType();
    InputStream is = uc.getInputStream();

    if (type == null) {
      if (ct != null) {
	type = ESXX.parseMIMEType(ct, params);
      }
      else {
	type = "application/octet-stream";
      }
    }

    //    JSESXX js_esxx = JSGlobal.getJSESXX(cx, thisObj);
    Object result  = esxx.parseStream(type, params,
				      is, jsuri.getURI(),
				      null,
				      null, //js_esxx.jsGet_debug(),
				      cx, thisObj);

    if (result == null) {
      return super.load(cx, thisObj, type, params);
    }
    else {
      return result;
    }
  }
}
