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
import javax.naming.NamingEnumeration;
import javax.naming.directory.*;
import org.esxx.*;
import org.esxx.js.*;
import org.esxx.util.XML;
import org.mozilla.javascript.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class LDAPHandler
  extends ProtocolHandler {
  public LDAPHandler(JSURI jsuri)
    throws URISyntaxException {
    super(jsuri);
  }

  @Override
  public Object load(Context cx, Scriptable thisObj,
		     String type, HashMap<String,String> params)
    throws Exception {
    ESXX esxx = ESXX.getInstance();

    // Default ldap: load() media type is XML
    if (type == null) {
      type = "text/xml";
    }

    if (!type.equals("text/xml")) {
      throw Context.reportRuntimeError("URI protocol '" + jsuri.getURI().getScheme() +
				       "' can only load 'text/xml'.");
    }

    Properties p = jsuri.getParams(cx, jsuri.getURI());
    Scriptable a = jsuri.getAuth(cx, jsuri.getURI(), null, null);

    if (a != null) {
      p.setProperty(javax.naming.Context.SECURITY_PRINCIPAL, 
		    Context.toString(a.get("username", a)));
      p.setProperty(javax.naming.Context.SECURITY_CREDENTIALS, 
		    Context.toString(a.get("password", a)));

      if (a.has("mechanism", a)) {
	p.setProperty(javax.naming.Context.SECURITY_AUTHENTICATION, 
		      Context.toString(a.get("mechanism", a)));
      }
      else {
	p.setProperty(javax.naming.Context.SECURITY_AUTHENTICATION, "simple");
      }
    }

    DirContext ctx = new InitialDirContext(p);
    NamingEnumeration<?> answer = ctx.search(jsuri.getURI().toString(), "", null);

    Document result = esxx.createDocument("result");
    Element  root   = result.getDocumentElement();

    while (answer.hasMore()) {
      SearchResult sr = (SearchResult) answer.next();

//      String name = sr.getName();
      String path = sr.getNameInNamespace();
      URI    euri = new URI(jsuri.getURI().getScheme(), jsuri.getURI().getAuthority(),
			    "/" + path, "?base", jsuri.getURI().getFragment());

      Element entry = result.createElementNS(null, "entry");

//       entry.setAttributeNS(null, "name", name);
//       entry.setAttributeNS(null, "path", path);
      entry.setAttributeNS(null, "uri", euri.toString());

      for (NamingEnumeration<?> ae = sr.getAttributes().getAll(); ae.hasMore();) {
	Attribute attr = (Attribute)ae.next();

	for (NamingEnumeration<?> e = attr.getAll(); e.hasMore();) {
	  Object v = e.next();

	  XML.addChild(entry, attr.getID(), v.toString());
	}
      }

      root.appendChild(entry);
    }

    return ESXX.domToE4X(result, cx, thisObj);
  }
}
