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
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.regex.Pattern;
import javax.mail.internet.ContentType;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.http.*;
import org.apache.http.auth.*;
import org.apache.http.client.*;
import org.apache.http.client.methods.*;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.*;
import org.apache.http.conn.params.*;
import org.apache.http.conn.routing.*;
import org.apache.http.conn.scheme.*;
import org.apache.http.conn.ssl.*;
import org.apache.http.entity.*;
import org.apache.http.impl.client.*;
import org.apache.http.impl.conn.tsccm.*;
import org.apache.http.params.*;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.esxx.ESXX;
import org.esxx.ESXXException;
import org.esxx.Response;
import org.esxx.js.*;
import org.esxx.oauth.OAuthCredentials;
import org.esxx.oauth.OAuthSchemeFactory;
import org.esxx.oauth.WRAPSchemeFactory;
import org.esxx.util.JS;
import org.mozilla.javascript.*;

public class HTTPHandler
  extends URLHandler {
  public HTTPHandler(JSURI jsuri)
    throws URISyntaxException {
    super(jsuri);

    synchronized (HTTPHandler.class) {
      if (preemptiveSchemes == null) {
	preemptiveSchemes = new PreemptiveSchemes();
	preemptiveInterceptor = new PreemptiveInterceptor();

	// Purge preemptive cache peridically
	ESXX.getInstance().getExecutor().scheduleWithFixedDelay(new Runnable() {
	    @Override public void run() {
	      preemptiveSchemes.purgeEntries();
	    }
	  }, 10, 10, java.util.concurrent.TimeUnit.SECONDS);
      }
    }
  }

  @Override
    public Object load(Context cx, Scriptable thisObj, ContentType recv_ct)
    throws Exception {
    Result result = sendRequest(cx, thisObj, recv_ct, new HttpGet(jsuri.getURI()));

    if (result.status < 200 || result.status >= 300) {
      throw new JavaScriptException(makeJSResponse(cx, thisObj, result), null, 0);
    }

    return result.object;
  }

  @Override
    public Object save(Context cx, Scriptable thisObj,
		       Object data, ContentType send_ct, ContentType recv_ct)
    throws Exception {
    HttpPut put = new HttpPut(jsuri.getURI());

    attachObject(data, send_ct, put, cx);

    Result result = sendRequest(cx, thisObj, recv_ct, put);

    if (result.status < 200 || result.status >= 300) {
      throw new JavaScriptException(makeJSResponse(cx, thisObj, result), null, 0);
    }

    return result.object;
  }

  @Override
    public Object append(Context cx, Scriptable thisObj,
			 Object data, ContentType send_ct, ContentType recv_ct)
    throws Exception {
    HttpPost post = new HttpPost(jsuri.getURI());

    attachObject(data, send_ct, post, cx);

    Result result = sendRequest(cx, thisObj, recv_ct, post);

    if (result.status < 200 || result.status >= 300) {
      throw new JavaScriptException(makeJSResponse(cx, thisObj, result), null, 0);
    }

    return result.object;
  }

  @Override
    public Object modify(Context cx, Scriptable thisObj,
			 Object data, ContentType send_ct, ContentType recv_ct)
    throws Exception {
    HttpPost patch = new HttpPost(jsuri.getURI()) {
	@Override public String getMethod() {
	  return "PATCH";
	}
      };

    attachObject(data, send_ct, patch, cx);

    Result result = sendRequest(cx, thisObj, recv_ct, patch);

    if (result.status < 200 || result.status >= 300) {
      throw new JavaScriptException(makeJSResponse(cx, thisObj, result), null, 0);
    }

    return result.object;
  }

  @Override
    public Object remove(Context cx, Scriptable thisObj,
			 ContentType recv_ct)
    throws Exception {
    Result result = sendRequest(cx, thisObj, recv_ct, new HttpDelete(jsuri.getURI()));

    if (result.status < 200 || result.status >= 300) {
      throw new JavaScriptException(makeJSResponse(cx, thisObj, result), null, 0);
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
    ContentType  send_ct  = null;
    ContentType  recv_ct  = null;

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
      send_ct = new ContentType(Context.toString(args[3]));
    }

    if (args.length >= 5) {
      recv_ct = new ContentType(Context.toString(args[4]));
    }

    HttpPost req = new HttpPost(jsuri.getURI()) {
	@Override public String getMethod() {
	  return method;
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
      attachObject(send_obj, send_ct, req, cx);
    }

    Result result = sendRequest(cx, thisObj, recv_ct, req);

    return makeJSResponse(cx, thisObj, result);
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
      //      sr.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));

      try {
	SSLContext sslcontext = SSLContext.getInstance(SSLSocketFactory.TLS);
	sslcontext.init(null, new TrustManager[] { new X509TrustManager() {
	    @Override public void checkServerTrusted(X509Certificate[] chain, String auth) {}

	    @Override public X509Certificate[] getAcceptedIssuers() {
	      return new X509Certificate[0];
	    }

	    @Override public void checkClientTrusted(X509Certificate[] certs, String auth) {}
	  } }, new java.security.SecureRandom());

	SSLSocketFactory ssf = new SSLSocketFactory(sslcontext, null);
	ssf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
	sr.register(new Scheme("https", ssf, 443));
      }
      catch (Exception ex) {
	ex.printStackTrace();
      }

      connectionManager = new ThreadSafeClientConnManager(getHttpParams(), sr);
    }

    return connectionManager;
  }

  public static synchronized void setConnectionManager(ClientConnectionManager c) {
    connectionManager = c;
  }

  private synchronized HttpClient getHttpClient() {
    if (httpClient == null) {
      httpClient = new DefaultHttpClient(getConnectionManager(), getHttpParams());

      httpClient.addRequestInterceptor(preemptiveInterceptor, 0);
      httpClient.setCredentialsProvider(new ESXXCredentialsProvider());
      httpClient.setTargetAuthenticationHandler(new ESXXAuthenticationHandler());
      httpClient.setCookieStore(new CookieJar(jsuri));

      httpClient.getAuthSchemes().register(OAuthSchemeFactory.SCHEME_NAME,
					   new OAuthSchemeFactory());
      httpClient.getAuthSchemes().register(WRAPSchemeFactory.SCHEME_NAME,
					   new WRAPSchemeFactory());
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
			     ContentType ct,
			     final HttpUriRequest msg)
    throws Exception {
    // Add HTTP headers
    jsuri.enumerateHeaders(cx, new JSURI.PropEnumerator() {
	public void handleProperty(Scriptable p, int s) {
	  msg.addHeader(Context.toString(p.get("name", p)),
			Context.toString(p.get("value", p)));
	}
      }, jsuri.getURI());

    HttpResponse response = getHttpClient().execute(msg);
    HttpEntity   entity   = response.getEntity();

    try {
      Result result  = new Result();
      result.status  = response.getStatusLine().getStatusCode();
      result.headers = response.getAllHeaders();

      if (entity != null && entity.getContentLength() != 0) {
	if (ct == null) {
	  Header hdr = entity.getContentType();
	  result.contentType = hdr == null ? "application/octet-stream" : hdr.getValue();

	  ct = new ContentType(result.contentType);
	}
	else {
	  result.contentType = ct.toString();
	}

	result.object = ESXX.getInstance().parseStream(ct, entity.getContent(), jsuri.getURI(),
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

  private void attachObject(Object data, ContentType ct,
			    HttpEntityEnclosingRequest request, Context cx)
    throws IOException {
    // FIXME: This may store the data three times in memory -- If
    // there were a way to turn the Object into an InputStream
    // instead, we would not have this problem.
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ct = ESXX.getInstance().serializeObject(data, ct, bos, true);
    ByteArrayEntity bae = new ByteArrayEntity(bos.toByteArray());
    bae.setContentType(ct.toString());
    request.setEntity(bae);
  }

  private static Scriptable makeJSResponse(Context cx, Scriptable scope, Result result) {
    Scriptable hdr = cx.newObject(scope);

    for (Header h : result.headers) {
      hdr.put(h.getName(), hdr, h.getValue());
    }

    return JSESXX.newObject(cx, scope, "Response", new Object[] {
	result.status, hdr, result.object, result.contentType
      });
  }

  private static class PreemptiveInterceptor
    implements HttpRequestInterceptor {
    @Override public void process(HttpRequest request, HttpContext context)
      throws HttpException, IOException {
      try {
	AuthState           as = (AuthState)
	  context.getAttribute(ClientContext.TARGET_AUTH_STATE);
	CredentialsProvider cp = (CredentialsProvider)
	  context.getAttribute(ClientContext.CREDS_PROVIDER);
	HttpHost          host = (HttpHost)
	  context.getAttribute(ExecutionContext.HTTP_TARGET_HOST);
	String        full_uri = host.toURI() + request.getRequestLine().getUri();

	if (as.getAuthScheme() == null) {
	  // This request does not have an AuthScheme, so lets see if
	  // we've already been here, and if so, add the apporopriate
	  // authentication header.

	  AuthScheme   scheme = null;
	  Credentials   creds = null;
	  PreemptiveSchemes.PreemptiveScheme ps = preemptiveSchemes.find(full_uri);

	  if (ps != null) {
	    // We've been here before; use known state
	    scheme = ps.getScheme();
	    creds  = cp.getCredentials(ps.getScope());
	  }
	  else {
	    // We've not been here before; check if the user wishes to
	    // force preemptive authentication
	    AuthScope scope = new AuthScope(host.getHostName(), host.getPort());
	    creds = cp.getCredentials(scope);

	    if (cp instanceof ESXXCredentialsProvider) {
	      ESXXCredentialsProvider ecp = (ESXXCredentialsProvider) cp;
	      AuthSchemeRegistry  authreg = (AuthSchemeRegistry)
		context.getAttribute(ClientContext.AUTHSCHEME_REGISTRY);

	      Auth auth = ecp.getAuth(scope);

	      if (auth.isPreemptive()) {
		HttpParams params = getHttpParams().copy();
		params.setParameter("defaultRealm", auth.getRealm());
		scheme = authreg.getAuthScheme(auth.getMechanism(), params);
	      }
	    }
	  }

	  if (scheme != null && creds != null) {
	    as.setAuthScheme(scheme);
	    as.setCredentials(creds);
	  }
	}
	else {
	  // This is a request with authenication available. Make a
	  // note of this for the next time we access this host.

	  preemptiveSchemes.remember(host.toURI(), // RFC 2617 says all host URLs
				     as.getAuthScheme(),
				     as.getAuthScope());
	}
      }
      catch (Exception ex) {
	ex.printStackTrace();
      }
    }
  }

  private class ESXXCredentialsProvider
    implements CredentialsProvider {
    @Override public void clear() {
      throw new UnsupportedOperationException("HttpURI.CredentialsProvider.clear()"
					      + " not implemented.");
    }

    @Override public void setCredentials(AuthScope authscope, Credentials credentials) {
      throw new UnsupportedOperationException("HttpURI.CredentialsProvider.setCredentials()"
					      + " not implemented.");
    }

    @Override public Credentials getCredentials(AuthScope authscope) {
      Auth auth = getAuth(authscope);

      if ("oauth".equalsIgnoreCase(auth.getMechanism())) {
	OAuthCredentials creds = new OAuthCredentials(auth.getUsername(), auth.getPassword());
	creds.getAccessor().accessToken = auth.getUsername2();
	creds.getAccessor().tokenSecret = auth.getPassword2();
	return creds;
      }
      else if (auth.getUsername() != null) {
	return new UsernamePasswordCredentials(auth.getUsername(), auth.getPassword());
      }
      else if (auth.getPassword() != null) {
	return new UsernamePasswordCredentials("", auth.getPassword());
      }
      else {
	return null;
      }
    }

    public Auth getAuth(AuthScope authscope) {
      try {
	Scriptable auth = jsuri.getAuth(Context.getCurrentContext(),
					new URI(jsuri.getURI().getScheme(),
						null,
						authscope.getHost(),
						authscope.getPort(),
						null, null, null),
					authscope.getRealm(), authscope.getScheme());
	return new Auth(auth);
      }
      catch (URISyntaxException ex) {
	throw new ESXXException("Failed to convert AuthScope to URI: " + ex.getMessage(), ex);
      }
    }
  }

  private class ESXXAuthenticationHandler
    extends DefaultTargetAuthenticationHandler {
      @Override public AuthScheme selectScheme(Map<String, Header> challenges,
					       HttpResponse response,
					       HttpContext context)
	throws AuthenticationException {
        AuthSchemeRegistry registry = (AuthSchemeRegistry)
	  context.getAttribute(ClientContext.AUTHSCHEME_REGISTRY);

        if (registry == null) {
	  throw new IllegalStateException("No AuthScheme registry");
        }

	Context cx = Context.getCurrentContext();
	HttpHost h = (HttpHost) context.getAttribute(ExecutionContext.HTTP_TARGET_HOST);
	URI    uri = URI.create(h.toURI());

	String[] mechanisms = jsuri.getAuthMechanisms(cx, uri, null, preferredSchemes);

	for (String mechanism : mechanisms) {
	  Header challenge = challenges.get(mechanism.toLowerCase(Locale.ENGLISH));

	  try {
	    AuthScheme scheme = registry.getAuthScheme(mechanism, response.getParams());
	    scheme.processChallenge(challenge);

	    if (jsuri.getAuth(cx, uri, scheme.getRealm(), scheme.getSchemeName()) != null) {
	      // We've found a scheme that works -- use it
	      return scheme;
	    }
	  }
	  catch (Exception ignored) {
	    // Try next mechanism/scheme
	  }
	}

	throw new AuthenticationException("Unable to find a suitable AuthScheme for the" +
					  " provided challenges");
      }

      private String[] preferredSchemes = new String[] {
	// Note: Lowercase names only, because of addAll() in JSURI.getAuthMechanisms()
	"ntlm", "digest", "oauth", "wrap", "basic"
      };
  }

  private static HttpParams httpParams;
  private static ClientConnectionManager connectionManager;
  private static PreemptiveSchemes preemptiveSchemes;
  private static PreemptiveInterceptor preemptiveInterceptor;
  private DefaultHttpClient httpClient;
  private BasicHttpContext httpContext;
}
