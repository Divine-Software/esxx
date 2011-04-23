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

import java.io.IOException;
import java.net.URI;
import java.net.Socket;
import java.net.URISyntaxException;
import java.util.HashMap;
import javax.mail.internet.ContentType;
import org.esxx.*;
import org.esxx.js.*;
import org.mozilla.javascript.*;

public class TCPHandler
  extends ProtocolHandler {
  public TCPHandler(JSURI jsuri)
    throws URISyntaxException {
    super(jsuri);
  }

  @Override public Object load(Context cx, Scriptable thisObj, ContentType recv_ct)
    throws Exception {
    URI    uri  = jsuri.getURI();
    String host = uri.getHost();
    int    port = uri.getPort();
    String path = uri.getPath();

    if (host == null || port == -1) {
      throw Context.reportRuntimeError("Missing host and/or port from URI");
    }

    if (path != null && !path.equals("/")) {
      throw Context.reportRuntimeError("URI contains a path component");
    }

    if (recv_ct == null) {
      recv_ct = binaryContentType;
    }

    Socket socket = new Socket(host, port);

    try {
      return ESXX.getInstance().parseStream(recv_ct,
					    socket.getInputStream(), jsuri.getURI(),
					    null, null, cx, thisObj);
    }
    finally {
      socket.close();
    }
  }

  @Override public Object save(Context cx, Scriptable thisObj,
			       Object data, ContentType send_ct, ContentType recv_ct)
    throws Exception {
    URI    uri  = jsuri.getURI();
    String host = uri.getHost();
    int    port = uri.getPort();
    String path = uri.getPath();

    if (host == null || port == -1) {
      throw Context.reportRuntimeError("Missing host and/or port from URI");
    }

    if (path != null && !path.equals("/")) {
      throw Context.reportRuntimeError("URI contains a path component");
    }

    if (recv_ct == null) {
      recv_ct = binaryContentType;
    }

    Socket socket = new Socket(host, port);

    try {
      ESXX esxx = ESXX.getInstance();
      //      java.io.InputStream is = ;
      send_ct = esxx.serializeObject(data, send_ct, socket.getOutputStream(), false);
      //      try { socket.shutdownOutput(); } catch (IOException ignored) {}
      return esxx.parseStream(recv_ct, socket.getInputStream(), jsuri.getURI(),
			      null, null, cx, thisObj);
    }
    finally {
      socket.close();
    }
  }
}
