
package org.blom.martin.esxx.js;

import java.net.URI;
import java.util.HashMap;
import java.util.Hashtable;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import org.blom.martin.esxx.ESXX;
import org.mozilla.javascript.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class LdapURI 
  extends JSURI {
    public LdapURI(ESXX esxx, URI uri) {
      super(esxx, uri);
    }

    protected Object load(Context cx, Scriptable thisObj,
			  String type, HashMap<String,String> params)
      throws Exception {
      Hashtable<String, String> env = new Hashtable<String, String>();
      boolean got_user = false;
      boolean got_type = false;

      // Default ldap: load() media type is XML
      if (type == null) {
	type = "text/xml";
      }

      if (type.equals("text/xml")) {
	for (Object id : ScriptableObject.getPropertyIds(thisObj)) {
	  if (id instanceof String) {
	    String key   = (String) id;
	    String value = Context.toString(ScriptableObject.getProperty(thisObj, key));

	    if (key.equals("user")) {
	      key = javax.naming.Context.SECURITY_PRINCIPAL;
	      got_user = true;
	    }
	    else if (key.equals("password")) {
	      key = javax.naming.Context.SECURITY_CREDENTIALS;
	    }
	    else if (key.equals("authentication")) {
	      key = javax.naming.Context.SECURITY_AUTHENTICATION;
	      got_type = true;
	    }

	    env.put(key, value);
	  }
	}

	if (got_user && !got_type) {
	  env.put(javax.naming.Context.SECURITY_AUTHENTICATION, "simple");
	}

	DirContext        ctx    = new InitialDirContext(env);
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
      else {
	throw Context.reportRuntimeError("URI protocol '" + uri.getScheme() + 
					 "' can only load 'text/xml'."); 
      }
    }
}
