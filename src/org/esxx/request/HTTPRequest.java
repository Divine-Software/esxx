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

package org.esxx.request;

import com.sun.net.httpserver.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import org.esxx.*;

public class HTTPRequest
  extends WebRequest {

  public HTTPRequest(HttpExchange he) {
    super(he.getRequestBody(), System.err);
    httpExchange = he;
  }

  public void initRequest(URI fs_root_uri, URI path_translated)
    throws URISyntaxException {

    URI helper_uri = new URI("http", httpExchange.getRequestHeaders().getFirst("Host"), 
			     "/", null, null);

    URI full_request_uri = new URI(helper_uri.getScheme()
				   + "://" + helper_uri.getRawAuthority() 
				   + httpExchange.getRequestURI().getRawPath()
				   + (httpExchange.getRequestURI().getRawQuery() != null ?
				      "?"  + httpExchange.getRequestURI().getRawQuery() : ""));

    InetSocketAddress local = httpExchange.getLocalAddress();
    String local_host = local.getAddress().toString().replaceFirst("[^/]*/", "");
    int    local_port = local.getPort();

    InetSocketAddress remote = httpExchange.getRemoteAddress();
    String remote_host = remote.getAddress().toString().replaceFirst("[^/]*/", "");
    int    remote_port = remote.getPort();

    Properties p = createCGIEnvironment(httpExchange.getRequestMethod(), 
					httpExchange.getProtocol(),
					full_request_uri,
					path_translated,
					local_host, local_port,
					remote_host, remote_port,
					fs_root_uri);

    // Add request headers
    for (Map.Entry<String, List<String>> e : httpExchange.getRequestHeaders().entrySet()) {
      p.setProperty(ESXX.httpToCGI(e.getKey()), e.getValue().get(0));
    }

    super.initRequest(httpExchange.getRequestMethod(), full_request_uri, path_translated,
		      p, fs_root_uri);
  }

  public Integer handleResponse(Response response)
    throws Exception {
    try {
      final Headers headers = httpExchange.getResponseHeaders();
      headers.set("Content-Type", response.getContentType(true));
      response.enumerateHeaders(new Response.HeaderEnumerator() {
	  public void header(String name, String value) {
	    headers.set(name, value);
	  }
	});

      int  status = response.getStatus();
      long content_length;

      if ((status >= 100 && status <= 199) ||
	  status == 204 ||
	  status == 205 ||
	  status == 304) {
	content_length = -1;
      }
      else if (response.isBuffered()) {
	content_length = response.getContentLength();
      }
      else {
	content_length = 0;
      }

      httpExchange.sendResponseHeaders(status, content_length);

      // Output body
      if (content_length != -1) {
	OutputStream os = httpExchange.getResponseBody();
	response.writeResult(os);
	try { os.close(); } catch (Exception ex) {}
      }

      return 0;
    }
    catch (IOException ex) {
      // If we fail to send response, it's probably just because
      // nobody is listening anyway.
      return 20;
    }
    finally {
      httpExchange.close();
    }
  }


  public static void runServer(int http_port, String fs_root)
    throws IOException, java.net.URISyntaxException {
    final ESXX    esxx = ESXX.getInstance();
    final String  root = new File(fs_root).getAbsolutePath();
    final URI root_uri = new File(root).toURI();

    HttpServer  hs = HttpServer.create(new InetSocketAddress(http_port), 0);
    hs.createContext("/", new HttpHandler() {
	public void handle(HttpExchange he)
	{
	  String req_uri_raw = he.getRequestURI().getRawPath();

	  HTTPRequest hr = new HTTPRequest(he);

	  try {
	    URI path_translated = root_uri.resolve(req_uri_raw.substring(1));
	    hr.initRequest(root_uri, path_translated);
	    esxx.addRequest(hr, hr, 0);
	    he = null;
	  }
	  catch (Exception ex) {
	    hr.reportInternalError(500, "ESXX Server Error", "HTTP Error",  ex.getMessage(), ex);
	    he = null;
	  }
	  finally {
	    if (he != null) { 
	      // Make sure he is always closed when the request ends
	      try { he.close(); } catch (Exception ex) {}
	    }
	  }
	}
      });

    int http_threads = Integer.parseInt(esxx.getSettings().getProperty("esxx.http_threads", "0"));

    if (http_threads == 0) {
      // Use an unbounded thread pool
      hs.setExecutor(Executors.newCachedThreadPool());
    }
    else {
      hs.setExecutor(Executors.newFixedThreadPool(http_threads));
    }

    esxx.getLogger().logp(java.util.logging.Level.INFO, null, null, 
			  "Listening for HTTP requests on port " + http_port);

    hs.start();

    while (true) {
      try {
	Thread.sleep(10000);
      }
      catch (InterruptedException ex) {
	Thread.currentThread().interrupt();
	break;
      }
    }
  }

  private HttpExchange httpExchange;
}
