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

package org.esxx.js;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import org.esxx.ESXX;
import org.esxx.Response;
import org.mozilla.javascript.*;

import org.apache.http.*;
import org.apache.http.auth.*;
import org.apache.http.client.*;
import org.apache.http.client.methods.*;
import org.apache.http.conn.*;
import org.apache.http.conn.params.*;
import org.apache.http.conn.ssl.*;
import org.apache.http.entity.*;
import org.apache.http.impl.client.*;
import org.apache.http.impl.conn.tsccm.*;
import org.apache.http.params.*;
import org.apache.http.protocol.*;

public class HttpURI
  extends UrlURI {
  public HttpURI(URI uri) {
    super(uri);
  }

  @Override
  protected Object load(Context cx, Scriptable thisObj,
			String type, HashMap<String,String> params)
    throws Exception {
    Result result = sendRequest(cx, thisObj, type, params, new HttpGet(uri));

    if (result.status / 100 != 2) {
      throw Context.reportRuntimeError("HTTP status code not 2xx.");
    }

    return result.object;
  }

  @Override
  protected Object save(Context cx, Scriptable thisObj,
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
  protected Object append(Context cx, Scriptable thisObj,
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
  protected Object query(Context cx, Scriptable thisObj, Object[] args)
    throws Exception {
    
    return null;
  }


  @Override
  protected Object remove(Context cx, Scriptable thisObj,
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
      HttpConnectionManagerParams.setMaxTotalConnections(httpParams, Integer.MAX_VALUE);
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
	  public void clear() {
	    throw new UnsupportedOperationException();
	  }

	  public void setCredentials(AuthScope authscope, Credentials credentials) {
	    throw new UnsupportedOperationException();
	  }

	  public Credentials getCredentials(AuthScope authscope) {
	    String username = "", password = "";
	    
	    System.out.println(authscope.getScheme());
	    System.out.println(authscope.getHost());
	    System.out.println(authscope.getPort());
	    System.out.println(authscope.getRealm());

	    return new UsernamePasswordCredentials(username, password);
	  }
	});
    }

    return httpClient;
  }

  private static class Result {
    public int status;
    public Header[] headers;
    public Object object;
  }

  private Result sendRequest(Context cx, Scriptable thisObj, 
			     String type, HashMap<String,String> params,
			     HttpUriRequest msg) 
    throws Exception {
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

	  type = ESXX.parseMIMEType(hdr == null ? "application/octet-stream" : hdr.getValue(), 
				    params);
	}

	ESXX   esxx    = ESXX.getInstance();
	JSESXX js_esxx = JSGlobal.getJSESXX(cx, thisObj);
	result.object  =  esxx.parseStream(type, params,
					   entity.getContent(), uri.toURL(),
					   null,
					   js_esxx.jsGet_debug(),
					   cx, this);
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
    entity.setHeader("Content-Type", response.getContentType());
    entity.setEntity(new ByteArrayEntity(bos.toByteArray()));
  }

  private static HttpParams httpParams;
  private static ClientConnectionManager connectionManager;
  private DefaultHttpClient httpClient;
}
