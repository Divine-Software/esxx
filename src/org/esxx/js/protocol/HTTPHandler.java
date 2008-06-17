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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import org.apache.http.*;
import org.apache.http.auth.*;
import org.apache.http.client.*;
import org.apache.http.client.methods.*;
import org.apache.http.conn.*;
import org.apache.http.conn.params.*;
import org.apache.http.conn.routing.*;
import org.apache.http.conn.scheme.*;
import org.apache.http.conn.ssl.*;
import org.apache.http.cookie.*;
import org.apache.http.entity.*;
import org.apache.http.impl.client.*;
import org.apache.http.impl.conn.tsccm.*;
import org.apache.http.impl.cookie.*;
import org.apache.http.params.*;
import org.apache.http.protocol.*;
import org.esxx.ESXX;
import org.esxx.ESXXException;
import org.esxx.Response;
import org.esxx.js.*;
import org.mozilla.javascript.*;

public class HTTPHandler
  extends URLHandler {
  public HTTPHandler(URI uri, JSURI jsuri) {
    super(uri, jsuri);
  }

  @Override
  public Object load(Context cx, Scriptable thisObj,
		       String type, HashMap<String,String> params)
    throws Exception {
    Result result = sendRequest(cx, thisObj, type, params, new HttpGet(uri));

    if (result.status / 100 != 2) {
      throw Context.reportRuntimeError("HTTP status code not 2xx.");
    }

    return result.object;
  }

  @Override
  public Object save(Context cx, Scriptable thisObj,
		       Object data, String type, HashMap<String,String> params)
    throws Exception {
    HttpPut put = new HttpPut(uri);

    attachObject(data, type, params, put, cx);

    Result result = sendRequest(cx, thisObj, null, null, put);
    
    if (result.status / 100 != 2) {
      throw Context.reportRuntimeError("HTTP status code not 2xx.");
    }

    return result.object;
  }

  @Override
  public Object append(Context cx, Scriptable thisObj,
			 Object data, String type, HashMap<String,String> params)
    throws Exception {
    HttpPost post = new HttpPost(uri);

    attachObject(data, type, params, post, cx);

    Result result = sendRequest(cx, thisObj, null, null, post);
    
    if (result.status / 100 != 2) {
      throw Context.reportRuntimeError("HTTP status code not 2xx.");
    }

    return result.object;
  }


  @Override
  public Object query(Context cx, Scriptable thisObj, Object[] args)
    throws Exception {
    if (args.length < 1) {
      throw Context.reportRuntimeError("Missing arguments to URI.query().");
    }

    final String method   = Context.toString(args[0]);
    Scriptable   headers  = null;
    Object       send_obj = null;
    String       send_ct  = null;
    String       recv_ct  = null;

    HashMap<String,String> send_params = new HashMap<String,String>();
    HashMap<String,String> recv_params = new HashMap<String,String>();

    if (args.length >= 2) {
      if (!(args[1] instanceof Scriptable)) {
	throw Context.reportRuntimeError("Second URI.query() argument must be an Object");
      }
      
      headers = (Scriptable) args[1];
    }

    if (args.length >= 3) {
      send_obj = args[2];
    }

    if (args.length >= 4) {
      send_ct = ESXX.parseMIMEType(Context.toString(args[3]), send_params);
    }

    if (args.length >= 5) {
      recv_ct = ESXX.parseMIMEType(Context.toString(args[4]), recv_params);
    }

    HttpPost req = new HttpPost() {
	@Override
	public String getMethod() {
	  return method;
	}

	public URI getURI() {
	  return uri;
	}
      };

    if (headers != null) {
      for (Object p : headers.getIds()) {
	if (p instanceof String) {
	  req.addHeader((String) p,
			Context.toString(headers.get((String) p, headers)));
	}
      }
    }

    if (send_obj != null && send_obj != Context.getUndefinedValue()) {
      attachObject(send_obj, send_ct, send_params, req, cx);
    }

    Result result = sendRequest(cx, thisObj, recv_ct, recv_params, req);

    Scriptable hdr = cx.newObject(thisObj);

    for (Header h : result.headers) {
      hdr.put(h.getName(), hdr, h.getValue());
    }

    return cx.newObject(thisObj, "Response", new Object[] { 
	result.status, hdr, result.object, result.contentType
      });
  }


  @Override
  public Object remove(Context cx, Scriptable thisObj,
			 String type, HashMap<String,String> params)
    throws Exception {
    Result result = sendRequest(cx, thisObj, type, params, new HttpDelete(uri));

    if (result.status / 100 != 2) {
      throw Context.reportRuntimeError("HTTP status code not 2xx.");
    }

    return result.object;
  }


  private static synchronized HttpParams getHttpParams() {
    if (httpParams == null) {
      httpParams = new BasicHttpParams();

      HttpProtocolParams.setVersion(httpParams, HttpVersion.HTTP_1_1);

      // No limits
      ConnManagerParams.setMaxTotalConnections(httpParams, Integer.MAX_VALUE);
      ConnManagerParams.setMaxConnectionsPerRoute(httpParams, new ConnPerRoute() {
	  public int getMaxForRoute(HttpRoute route) {
	    return Integer.MAX_VALUE;
	  }
	});
    }

    return httpParams;
  }

  private static synchronized ClientConnectionManager getConnectionManager() {
    if (connectionManager == null) {
      SchemeRegistry sr = new SchemeRegistry();
      sr.register(new Scheme("http",  PlainSocketFactory.getSocketFactory(), 80));
      sr.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));

      connectionManager = new ThreadSafeClientConnManager(getHttpParams(), sr);
    }

    return connectionManager;
  }

  private synchronized HttpClient getHttpClient() {
    if (httpClient == null) {
      httpClient = new DefaultHttpClient(getConnectionManager(), getHttpParams());
      
      httpClient.setCredentialsProvider(new CredentialsProvider() {
	  @Override public void clear() {
	    throw new UnsupportedOperationException("HttpURI.CredentialsProvider.clear()"
						    + " not implemented.");
	  }

	  @Override public void setCredentials(AuthScope authscope, Credentials credentials) {
	    throw new UnsupportedOperationException("HttpURI.CredentialsProvider.setCredentials()"
						    + " not implemented.");
	  }

	  @Override public Credentials getCredentials(AuthScope authscope) {
	    try {
	      Scriptable auth = jsuri.getAuth(Context.getCurrentContext(),
					      new URI(authscope.getScheme(),
						      null,
						      authscope.getHost(),
						      authscope.getPort(),
						      null, null, null),
					      authscope.getRealm());

	      if (auth == null) {
		return null;
	      }

	      return new UsernamePasswordCredentials(Context.toString(auth.get("username", auth)),
						     Context.toString(auth.get("password", auth)));
	    }
	    catch (URISyntaxException ex) {
	      throw new ESXXException("Failed to convert AuthScope to URI: " + ex.getMessage(), ex);
	    }
	  }
	});

      httpClient.setCookieStore(new CookieJar(jsuri, uri));
    }

    return httpClient;
  }

  private static class Result {
    public int status;
    public Header[] headers;
    public String contentType;
    public Object object;
  }

  private Result sendRequest(Context cx, Scriptable thisObj, 
			     String type, HashMap<String,String> params,
			     final HttpUriRequest msg) 
    throws Exception {
    // Add HTTP headers
    jsuri.enumerateHeaders(cx, new JSURI.PropEnumerator() {
	public void handleProperty(Scriptable p, int s) {
	  msg.addHeader(Context.toString(p.get("name", p)),
			Context.toString(p.get("value", p)));
	}
      }, uri);

    HttpResponse response = getHttpClient().execute(msg);
    StatusLine   status   = response.getStatusLine();
    HttpEntity   entity   = response.getEntity();

    if (params == null) {
      params = new HashMap<String,String>();
    }

    try {
      Result result  = new Result();
      result.status  = response.getStatusLine().getStatusCode();
      result.headers = response.getAllHeaders();

      if (entity.getContentLength() != 0) {
	if (type == null) {
	  Header hdr = entity.getContentType();
	  type = hdr == null ? "application/octet-stream" : hdr.getValue();
	}

	result.contentType = type;

	type = ESXX.parseMIMEType(type, params);

	ESXX   esxx    = ESXX.getInstance();
	//	JSESXX js_esxx = JSGlobal.getJSESXX(cx, thisObj);
	result.object  =  esxx.parseStream(type, params,
					   entity.getContent(), uri.toURL(),
					   null,
					   null,//js_esxx.jsGet_debug(),
					   cx, thisObj);
      }

      return result;
    }
    finally {
      if (entity != null) {
	entity.consumeContent();
      }
    }
  }
  
  private void attachObject(Object data, String type, HashMap<String,String> params,
			    HttpEntityEnclosingRequest entity, Context cx) 
    throws IOException {
    // FIXME: This may store the data three times in memory -- If
    // there were a way to turn the Object into an InputStream
    // instead, we would not have this problem.
    ESXX esxx = ESXX.getInstance();
    Response response = new Response(0, type, data, null);
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    //    Response.writeObject(data, type, params, esxx, cx, bos);
    response.writeResult(esxx, cx, bos);
    entity.setHeader("Content-Type", response.getContentType(true));
    entity.setEntity(new ByteArrayEntity(bos.toByteArray()));
  }

  private static HttpParams httpParams;
  private static ClientConnectionManager connectionManager;
  private DefaultHttpClient httpClient;
}
