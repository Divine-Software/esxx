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
import javax.mail.internet.ContentType;
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
  public Object load(Context cx, Scriptable thisObj, ContentType recv_ct)
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

    if (recv_ct == null) {
      if (uri_type.length() == 0) {
	uri_type = "text/plain;charset=US-ASCII";
      }

      recv_ct = new ContentType(uri_type);
    }

    InputStream is;
    String charset = recv_ct.getParameter("charset");

    if (charset == null) {
      charset = "UTF-8";
    }

    is = new ByteArrayInputStream(uri_data.getBytes(charset));

    if (uri_b64) {
      is = new Base64.InputStream(is, Base64.DECODE);
    }

    Object result  = ESXX.getInstance().parseStream(recv_ct, is, uri,
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

  @Override
  public Object save(Context cx, Scriptable thisObj,
		     Object data, ContentType send_ct, ContentType recv_ct)
    throws Exception {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    send_ct = ESXX.getInstance().serializeObject(data, send_ct, bos);

    String b64 = Base64.encodeBytes(bos.toByteArray(), Base64.DONT_BREAK_LINES);

    // Replace URI and return result as a string
    URI uri = new URI("data", send_ct + ";base64," + b64, null);
    jsuri.setURI(uri);

    return null;
  }
}
