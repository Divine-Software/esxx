/*
     ESXX - The friendly ECMAscript/XML Application Server
     Copyright (C) 2007-2015 Martin Blom <martin@blom.org>

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
import javax.mail.internet.ContentType;
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
  public Object load(Context cx, Scriptable thisObj, ContentType recv_ct)
    throws Exception {
    ESXX        esxx = ESXX.getInstance();
    URL          url = jsuri.getURI().toURL();
    URLConnection uc = url.openConnection();

    uc.setDoInput(true);
    uc.setDoOutput(false);
    uc.connect();

    InputStream is = uc.getInputStream();

    if (recv_ct == null) {
      if (uc.getContentType() != null) {
	recv_ct = new ContentType(uc.getContentType());
      }
      else {
	recv_ct = binaryContentType;
      }
    }

    //    JSESXX js_esxx = JSGlobal.getJSESXX(cx, thisObj);
    Object result  = esxx.parseStream(recv_ct, is, jsuri.getURI(),
				      null,
				      null, //js_esxx.jsGet_debug(),
				      cx, thisObj);

    if (result == null) {
      return super.load(cx, thisObj, recv_ct);
    }
    else {
      return result;
    }
  }
}
