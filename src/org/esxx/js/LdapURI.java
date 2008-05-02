/*
     ESXX - The friendly ECMAscript/XML Application Server
     Copyright (C) 2007-2008 Martin Blom <martin@blom.org>
     
     This program is free software; you can redistribute it and/or
     modify it under the terms of the GNU General Public License
     as published by the Free Software Foundation; either version 2
     of the License, or (at your option) any later version.
     
     This program is distributed in the hope that it will be useful,
     but WITHOUT ANY WARRANTY; without even the implied warranty of
     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
     GNU General Public License for more details.
     
     You should have received a copy of the GNU General Public License
     along with this program; if not, write to the Free Software
     Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
*/

package org.esxx.js;

import java.net.URI;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Properties;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import org.esxx.ESXX;
import org.mozilla.javascript.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class LdapURI 
  extends JSURI {
    public LdapURI(URI uri) {
      super(uri);
    }

    protected Object load(Context cx, Scriptable thisObj,
			  String type, HashMap<String,String> params)
      throws Exception {
      ESXX esxx = ESXX.getInstance();

      // Default ldap: load() media type is XML
      if (type == null) {
	type = "text/xml";
      }

      if (!type.equals("text/xml")) {
	throw Context.reportRuntimeError("URI protocol '" + uri.getScheme() + 
					 "' can only load 'text/xml'."); 
      }
 
      DirContext        ctx    = new InitialDirContext(getProperties(thisObj));
      NamingEnumeration answer = ctx.search(uri.toString(), "", null);

      Document          result = esxx.createDocument("result");
      Element           root   = result.getDocumentElement();
	  
      while (answer.hasMore()) {
	SearchResult sr = (SearchResult) answer.next();

	String name = sr.getName();
	String path = sr.getNameInNamespace();
	URI    euri = new URI(uri.getScheme(), uri.getAuthority(),
			      "/" + path, "?base", uri.getFragment());

	Element entry = result.createElement("entry");

	entry.setAttribute("name", name);
	entry.setAttribute("path", path);
	entry.setAttribute("uri", euri.toString());

	for (NamingEnumeration ae = sr.getAttributes().getAll(); ae.hasMore();) {
	  Attribute attr = (Attribute)ae.next();

	  for (NamingEnumeration e = attr.getAll(); e.hasMore();) {
	    Object v = e.next();	

	    addChild(entry, attr.getID(), v.toString());
	  }
	}

	root.appendChild(entry);
      }
      
      return esxx.domToE4X(result, cx, this);
    }
}
