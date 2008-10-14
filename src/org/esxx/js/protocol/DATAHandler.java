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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import org.esxx.*;
import org.esxx.js.*;
import org.esxx.util.StringUtil;
import org.esxx.xmtp.Base64;
import org.mozilla.javascript.*;

public class DATAHandler
  extends ProtocolHandler {
  public DATAHandler(JSURI jsuri)
    throws URISyntaxException {
    super(jsuri);
  }

  @Override
  public Object load(Context cx, Scriptable thisObj,
		     String type, HashMap<String,String> params)
    throws Exception {
    URI         uri = jsuri.getURI();
    String specific = uri.getRawSchemeSpecificPart();
    int       comma = specific.lastIndexOf(',');

    boolean uri_b64 = false;
    String uri_type = "";
    String uri_data = "";

    if (comma != -1) {
      uri_type = StringUtil.decodeURI(specific.substring(0, comma), false);
    }

    if (specific.length() > 0) {
      uri_data = StringUtil.decodeURI(specific.substring(comma + 1), false);
    }

    if (uri_type.endsWith(";base64")) {
      uri_b64  = true;
      uri_type = uri_type.substring(0, uri_type.lastIndexOf(';'));
    }

    if (type == null) {
      if (uri_type.length() == 0) {
	uri_type = "text/plain;charset=US-ASCII";
      }

      type = ESXX.parseMIMEType(uri_type, params);
    }

    InputStream is;
    String charset = params.get("charset");

    if (charset == null) {
      charset = "UTF-8";
    }

    is = new ByteArrayInputStream(uri_data.getBytes(charset));

    if (uri_b64) {
      is = new Base64.InputStream(is, Base64.DECODE);
    }

    Object result  = ESXX.getInstance().parseStream(type, params,
						    is, uri,
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

  @Override
  public Object save(Context cx, Scriptable thisObj,
		     Object data, String type, HashMap<String,String> params)
    throws Exception {
    ESXX esxx = ESXX.getInstance();
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    Response response = new Response(0, type, data, null);

    //    Response.writeObject(data, type, params, esxx, cx, bos);
    response.writeResult(esxx, cx, bos);

    if (type == null) {
      type = ESXX.parseMIMEType(response.getContentType(true), params);
    }

    String b64 = Base64.encodeBytes(bos.toByteArray());

    // Attach parameters
    for (java.util.Map.Entry<String, String> p : params.entrySet()) {
      type = type + "; " + p.getKey() + "\"" + p.getValue() + "\"";
    }

    // Replace URI and return result as a string
    URI uri = new URI("data", type + ";base64," + b64, null);
    jsuri.setURI(uri);

    return null;
  }
}
