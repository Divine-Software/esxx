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
import java.util.*;
import org.apache.http.client.*;
import org.apache.http.cookie.*;
import org.apache.http.impl.cookie.*;
import org.esxx.ESXXException;
import org.esxx.js.*;
import org.mozilla.javascript.*;

class CookieJar
  implements CookieStore {
  public CookieJar(JSURI jsuri, URI uri) {
    this.jsuri = jsuri;
    this.uri   = uri;
  }

  @Override public synchronized void addCookie(Cookie cookie) {
    if (cookie == null) {
      return;
    }

    try {
      Context     cx = Context.getCurrentContext();
      Scriptable jar = jsuri.getCookieJar(cx, uri);

      if (jar != null) {
	// Clear equivalent cookies
	purge(cx, jar, null, cookie.getName(), cookie.getDomain());

	// Add new cookie unless it has already expired
	if (!cookie.isExpired(new Date())) {
	  Object len = jar.get("length", jar);

	  if (len != Scriptable.NOT_FOUND) {
	    int length = (int) Context.toNumber(len);

	    jar.put(length, jar, cookieToScriptable(cx, cookie));
	  }
	}
      }
    }
    catch (RuntimeException ex) {
      throw new ESXXException("Failed to add cookie: " + ex.getMessage(), ex);
    }
  }
	  
  @Override public synchronized void clear() {
    try {
      Context     cx = Context.getCurrentContext();
      Scriptable jar = jsuri.getCookieJar(cx, uri);

      if (jar != null) {
	jar.put("length", jar, 0);
      }
    }
    catch (RuntimeException ex) {
      throw new ESXXException("Failed to clear cookies: " + ex.getMessage(), ex);
    }
  }

  @Override public synchronized boolean clearExpired(Date date) {
    try {
      Context     cx  = Context.getCurrentContext();
      Scriptable  jar = jsuri.getCookieJar(cx, uri);

      return purge(cx, jar, date, null, null);
    }
    catch (RuntimeException ex) {
      throw new ESXXException("Failed to purge cookies: " + ex.getMessage(), ex);
    }
  }

  @Override public synchronized List<Cookie> getCookies() {
    ArrayList<Cookie> result = new ArrayList<Cookie>();

    try {
      Context     cx = Context.getCurrentContext();
      Scriptable jar = jsuri.getCookieJar(cx, uri);

      if (jar != null) {
	Object len = jar.get("length", jar);

	if (len != Scriptable.NOT_FOUND) {
	  int length = (int) Context.toNumber(len);

	  for (int i = 0; i < length; ++i) {
	    Object o = jar.get(i, jar);

	    if (o instanceof Scriptable) {
	      result.add(scriptableToCookie(cx, (Scriptable) o));
	    }
	  }
	}
      }
	
      return result;
    }
    catch (RuntimeException ex) {
      throw new ESXXException("Failed to retrieve cookies: " + ex.getMessage(), ex);
    }
  }


  /** Purges cookies from the cookie jar.
   *
   *  @param cx     A Context
   *  @param jar    The Scriptable cookie jar
   *  @param date   If not null, expired cookies (given this Date) will be purged
   *  @param name   If not null, all cookies matching name and domain will be purged
   *  @param domain The cookie domain. Domain names are case insensitive and null equals ""
   *
   *  @returns true if one or more cookies were purged
   */
    
  private boolean purge(Context cx, Scriptable jar, Date date, String name, String domain) {
    boolean purged = false;

    if (domain == null) {
      domain = "";
    }

    if (jar != null) {
      Object len = jar.get("length", jar);

      if (len != Scriptable.NOT_FOUND) {
	int length = (int) Context.toNumber(len);
	int j      = 0;

	for (int i = 0; i < length; ++i) {
	  Object o = jar.get(i, jar);

	  if (o instanceof Scriptable) {
	    Scriptable cookie  = (Scriptable) o;
	    Object     edate   = cookie.get(ClientCookie.EXPIRES_ATTR, cookie);
	    Object     cname   = cookie.get("name", cookie);
	    Object     cdomain = cookie.get("domain", cookie);

	    if (cdomain == Scriptable.NOT_FOUND) {
	      cdomain = "";
	    }
	    else {
	      cdomain = Context.toString(cdomain);
	    }

	    if (date != null &&
		edate != Scriptable.NOT_FOUND &&
		Context.toNumber(edate) <= date.getTime()) {
	      // Cookie has expired
	      purged = true;
	    }
	    else if (name != null &&
		     cname != Scriptable.NOT_FOUND &&
		     Context.toString(cname).equals(name) &&
		     domain.equalsIgnoreCase((String) cdomain)) {
	      // Cookie matches the name/domain parameters
	      purged = true;
	    }
	    else {
	      if (i != j) {
		jar.put(j, jar, o);
	      }
		
	      ++j;
	    }
	  }
	}

	jar.put("length", jar, j);
      }
    }

    return purged;
  }
    
  private Scriptable cookieToScriptable(Context cx, Cookie cookie) {
    Scriptable js  = cx.newObject(jsuri);
    Scriptable raw = cx.newObject(js);
      
    js.put("raw", js, raw);
      
    setValue(cx, cookie, js, raw, "name",  cookie.getName());
    setValue(cx, cookie, js, raw, "value", cookie.getValue());
		           
    setValue(cx, cookie, js, raw, ClientCookie.COMMENT_ATTR,    cookie.getComment());
    setValue(cx, cookie, js, raw, ClientCookie.COMMENTURL_ATTR, cookie.getCommentURL());
    setValue(cx, cookie, js, raw, ClientCookie.DISCARD_ATTR,    null);
    setValue(cx, cookie, js, raw, ClientCookie.DOMAIN_ATTR,     cookie.getDomain());
    setValue(cx, cookie, js, raw, ClientCookie.EXPIRES_ATTR,    cookie.getExpiryDate());
    setValue(cx, cookie, js, raw, ClientCookie.MAX_AGE_ATTR,    null);
    setValue(cx, cookie, js, raw, ClientCookie.PATH_ATTR,       cookie.getPath());
    setValue(cx, cookie, js, raw, ClientCookie.PORT_ATTR,       cookie.getPorts());
    setValue(cx, cookie, js, raw, ClientCookie.SECURE_ATTR,     cookie.isSecure());
    setValue(cx, cookie, js, raw, ClientCookie.VERSION_ATTR,    cookie.getVersion());

    return js;
  }

  private Cookie scriptableToCookie(Context cx, Scriptable js) {
    String name  = Context.toString(js.get("name", js));
    String value = Context.toString(js.get("value", js));
    Object raw   = js.get("raw", js);

    BasicClientCookie cookie;

    if (js.has(ClientCookie.COMMENTURL_ATTR, js) ||
	js.has(ClientCookie.DISCARD_ATTR, js) ||
	js.has(ClientCookie.PORT_ATTR, js)) {
      BasicClientCookie2 cookie2 = new BasicClientCookie2(name, value);

      cookie2.setCommentURL(stringValue(cx, js, raw, cookie2, ClientCookie.COMMENTURL_ATTR));
      cookie2.setDiscard(  booleanValue(cx, js, raw, cookie2, ClientCookie.DISCARD_ATTR));
      cookie2.setPorts(   intArrayValue(cx, js, raw, cookie2, ClientCookie.PORT_ATTR));
      cookie = cookie2;
    }
    else {
      cookie = new BasicClientCookie(name, value);
    }

    cookie.setComment(     stringValue(cx, js, raw, cookie, ClientCookie.COMMENT_ATTR));
    cookie.setDomain(      stringValue(cx, js, raw, cookie, ClientCookie.DOMAIN_ATTR));
    cookie.setExpiryDate(    dateValue(cx, js, raw, cookie, ClientCookie.EXPIRES_ATTR));
    cookie.setPath(        stringValue(cx, js, raw, cookie, ClientCookie.PATH_ATTR));
    cookie.setSecure(     booleanValue(cx, js, raw, cookie, ClientCookie.SECURE_ATTR));
    cookie.setVersion(        intValue(cx, js, raw, cookie, ClientCookie.VERSION_ATTR));

    setRawValue(raw, cookie, ClientCookie.MAX_AGE_ATTR);

    return cookie;
  }

  private void setValue(Context cx, Cookie cookie, Scriptable js, Scriptable raw,
			String name, Object value) {
    if (value instanceof String ||
	value instanceof Number ||
	value instanceof Boolean) {
      js.put(name, js, value);
    }
    else if (value instanceof Date) {
      js.put(name, js, ((Date) value).getTime());
    }
    else if (value instanceof int[]) {
      int[] ports = (int[]) value;
      Object[] ip = new Object[ports.length];

      for (int i = 0 ; i < ports.length; ++i) {
	ip[i] = (Integer) i;
      }

      js.put(name, js, cx.newArray(jsuri, ip));
    }

    // Add raw attribute, if present
    if (cookie instanceof ClientCookie) {
      ClientCookie cc = (ClientCookie) cookie;

      if (cc.containsAttribute(name)) {
	raw.put(name, raw, cc.getAttribute(name));
      }
    }
  }

  private void setRawValue(Object raw, BasicClientCookie cookie, String name) {
    if (raw instanceof Scriptable) {
      Object value = ((Scriptable) raw).get(name, (Scriptable) raw);

      if (value != Scriptable.NOT_FOUND) {
	cookie.setAttribute(name, Context.toString(value));
      }
    }
  }

  private String stringValue(Context cx, Scriptable js, Object raw, 
			     BasicClientCookie cookie, String name) {
    setRawValue(raw, cookie, name);

    Object value = js.get(name, js);

    if (value == Scriptable.NOT_FOUND) {
      return null;
    }
    else {
      return Context.toString(value);
    }
  }

  private int intValue(Context cx, Scriptable js, Object raw, 
		       BasicClientCookie cookie, String name) {
    setRawValue(raw, cookie, name);

    Object value = js.get(name, js);

    if (value == Scriptable.NOT_FOUND) {
      return 0;
    }
    else {
      return (int) Context.toNumber(value);
    }
  }

  private int[] intArrayValue(Context cx, Scriptable js, Object raw, 
			      BasicClientCookie cookie, String name) {
    setRawValue(raw, cookie, name);

    Object value = js.get(name, js);

    if (!(value instanceof Scriptable)) {
      return null;
    }
    else {
      Object[] elements = cx.getElements((Scriptable) value);
      int[]    result   = new int[elements.length];

      for (int i = 0; i < elements.length; ++i) {
	result[i] = (int) Context.toNumber(elements[i]);
      }

      return result;
    }
  }

  private Date dateValue(Context cx, Scriptable js, Object raw, 
			 BasicClientCookie cookie, String name) {
    setRawValue(raw, cookie, name);

    Object value = js.get(name, js);

    if (value == Scriptable.NOT_FOUND) {
      return null;
    }
    else {
      return new Date((long) Context.toNumber(value));
    }
  }

  private boolean booleanValue(Context cx, Scriptable js, Object raw, 
			       BasicClientCookie cookie, String name) {
    setRawValue(raw, cookie, name);

    return Context.toBoolean(js.get(name, js));
  }

  private JSURI jsuri;
  private URI uri;
}
