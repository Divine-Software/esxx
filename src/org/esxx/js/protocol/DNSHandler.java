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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Properties;
import javax.mail.internet.ContentType;
import javax.naming.NamingEnumeration;
import javax.naming.Binding;
import javax.naming.directory.*;
import org.esxx.*;
import org.esxx.js.*;
import org.esxx.util.StringUtil;
import org.esxx.util.XML;
import org.esxx.xmtp.Base64;
import org.mozilla.javascript.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class DNSHandler
  extends ProtocolHandler {

  public DNSHandler(JSURI jsuri)
    throws URISyntaxException {
    super(jsuri);
  }

  @Override public Object load(Context cx, Scriptable thisObj, ContentType recv_ct)
    throws Exception {
    recv_ct = ensureRecvTypeIsXML(recv_ct);

    ESXX esxx = ESXX.getInstance();

    try {
      String query = jsuri.getURI().getQuery();
      String[]  as = query == null ? new String[] { null, null } : query.split("\\?", 2);
      String[] att = as[0] == null || as[0].isEmpty() ? null : as[0].split(",");
      String scope = as.length < 2 || as[1] == null ? "" : as[1];
      URI      uri = new URI(jsuri.getURI().getScheme(),
			     jsuri.getURI().getAuthority(),
			     jsuri.getURI().getPath(), 
			     null /* query */,
			     null /* fragment */);

      Properties    p = jsuri.getParams(cx, jsuri.getURI());
      //      p.setProperty(javax.naming.Context.PROVIDER_URL, uri.toString());
      DirContext  ctx = new InitialDirContext(p);
      Document result = esxx.createDocument("result");

      if ("".equals(scope) || "base".equals(scope)) {
	addEntry(result, ctx, uri, att);
      }
      else if ("one".equals(scope)) {
	addEntry(result, ctx, uri, att);
	addEntries(result, ctx, uri, att, false);
      }
      else if ("sub".equals(scope)) {
	addEntry(result, ctx, uri, att);
	addEntries(result, ctx, uri, att, true);
      }
      else {
	throw Context.reportRuntimeError("The DNS URI scope must be empty, 'base', 'one' or 'sub'");
      }

      return ESXX.domToE4X(result, cx, thisObj);
    }
    catch (javax.naming.CommunicationException ex) {
      throw new ESXXException(504 /* Gateway Timeout */, ex.getMessage(), ex);
    }
    catch (javax.naming.NamingException ex) {
      throw new ESXXException(502 /* Bad Gateway */, ex.getMessage(), ex);
    }
    catch (URISyntaxException ex) {
      throw Context.reportRuntimeError("The URI " + jsuri.getURI().toString() 
				       + " is not a valid DNS URI.");
    }
  }

  private void addEntries(Document result, DirContext ctx, 
			  URI uri, String[] att, boolean recursive)
    throws URISyntaxException, javax.naming.NamingException {
    try {
      for (NamingEnumeration<Binding> ne = ctx.listBindings(uri.toString());
	   ne.hasMore(); ) {
	Binding b = ne.next();

	URI sub_uri = new URI(uri.getScheme(), 
			      uri.getAuthority(),
			      "/" + b.getNameInNamespace(),
			      null, null);

	addEntry(result, ctx, sub_uri, att);

	if (recursive) {
	  addEntries(result, ctx, sub_uri, att, true);
	}
      }
    }
    catch (NullPointerException ex) {
      // ne.hasMore() throws??
    }
  }

  private void addEntry(Document result, DirContext ctx, 
			URI uri, String[] att) 
    throws URISyntaxException, javax.naming.NamingException {

    URI euri = new URI(uri.getScheme(), 
		       uri.getAuthority(),
		       uri.getPath(),
 		       "?base",
 		       uri.getFragment());

    Element entry = result.createElementNS(null, "entry");

    //       entry.setAttributeNS(null, "name", name);
    //       entry.setAttributeNS(null, "path", path);
    entry.setAttributeNS(null, "uri", euri.toString());

    for (NamingEnumeration<?> ae = ctx.getAttributes(uri.toString(), att).getAll(); 
	 ae.hasMore();) {
      Attribute attr = (Attribute) ae.next();

      for (NamingEnumeration<?> e = attr.getAll(); e.hasMore();) {
	Object v = e.next();

	if (v instanceof byte[]) {
	  v = Base64.encodeBytes((byte[]) v, 0);
	}

	XML.addChild(entry, StringUtil.makeXMLName(attr.getID().toLowerCase(), ""), v.toString());
      }
    }

    result.getDocumentElement().appendChild(entry);
  }
}
