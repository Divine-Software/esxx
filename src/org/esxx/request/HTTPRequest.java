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
import javax.activation.FileTypeMap;
import javax.activation.MimetypesFileTypeMap;
import org.esxx.*;
import org.esxx.util.IO;
import org.mozilla.javascript.*;

public class HTTPRequest
  extends WebRequest {

  public HTTPRequest(URI root_uri, URI script_filename, URI path_translated, HttpExchange he)
    throws IOException {
    super(script_filename, null, createProperties(root_uri, script_filename, path_translated, he),
	  he.getRequestBody(),
	  System.err,
	  null);
    httpExchange = he;
  }

  public Integer handleResponse(ESXX esxx, Context cx, Response response)
    throws Exception {
    try {
      // Do not call super method, since it has a null OutputStream.

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
	content_length = response.getContentLength(esxx, cx);
      }
      else {
	content_length = 0;
      }

      httpExchange.sendResponseHeaders(status, content_length);

      // Output body
      try {
	if (content_length != -1) {
	  OutputStream os = httpExchange.getResponseBody();
	  response.writeResult(esxx, cx, os);
	  try { os.close(); } catch (Exception ex) {}
	}
      }
      finally {
	httpExchange.close();
      }

      return 0;
    }
    catch (Exception ex) {
      ex.printStackTrace();
      throw ex;
    }
  }

  private static Properties createProperties(URI root_uri,
					     URI script_filename,
					     URI path_translated,
					     HttpExchange he) {
    Properties p = new Properties();
    InetSocketAddress local  = he.getLocalAddress();
    InetSocketAddress remote = he.getRemoteAddress();
    String      query_string = he.getRequestURI().getRawQuery();

    if (query_string == null) {
      query_string = "";
    }

    String script_name = "/" + root_uri.relativize(script_filename).toString();
    String path_info   = "/" + script_filename.relativize(path_translated).toString();

    p.setProperty("GATEWAY_INTERFACE", "CGI/1.1");
    p.setProperty("SERVER_SOFTWARE",   "ESXX/1.0");
    p.setProperty("REQUEST_METHOD",    he.getRequestMethod());
    p.setProperty("REQUEST_URI",       he.getRequestURI().toString());
    p.setProperty("SERVER_PROTOCOL",   he.getProtocol());
    p.setProperty("REMOTE_ADDR",       remote.getAddress().toString().replaceFirst("[^/]*/", ""));
    p.setProperty("REMOTE_PORT",       "" + remote.getPort());
    p.setProperty("SERVER_ADDR",       local.getAddress().toString().replaceFirst("[^/]*/", ""));
    p.setProperty("SERVER_PORT",       "" + local.getPort());
    p.setProperty("PATH_TRANSLATED",   path_translated.getPath());
    p.setProperty("SCRIPT_FILENAME",   script_filename.getPath());
    p.setProperty("PATH_INFO",         path_info);
    p.setProperty("SCRIPT_NAME",       script_name);
    p.setProperty("QUERY_STRING",      query_string);

    for (Map.Entry<String, List<String>> e : he.getRequestHeaders().entrySet()) {
      String key   = e.getKey();
      String value = e.getValue().get(0);

      if (key.equals("Content-Type")) {
	key = "CONTENT_TYPE";
      }
      else if (key.equals("Content-Length")) {
	key = "CONTENT_LENGTH";
      }
      else if (key.equals("Host")) {
	p.setProperty("SERVER_NAME", value.replaceFirst(":.*", ""));
	key = "HTTP_HOST";
      }
      else {
	key = "HTTP_" + key.toUpperCase().replaceAll("-", "_");
      }

      p.setProperty(key, value);
    }

    return p;
  }

  private static final java.text.SimpleDateFormat isoFormat =
    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  private static final FileTypeMap fileTypeMap = new ESXXFileTypeMap();

  public static void runServer(int http_port, String fs_root)
    throws IOException, java.net.URISyntaxException {
    final ESXX    esxx = ESXX.getInstance();
    final String  root = new File(fs_root).getCanonicalPath();
    final URI root_uri = new File(root).toURI();

    HttpServer  hs = HttpServer.create(new InetSocketAddress(http_port), 0);
    hs.createContext("/", new HttpHandler() {
	public void handle(HttpExchange he)
	  throws IOException {
	  String req_uri_raw = he.getRequestURI().getRawPath();
	  String req_uri_xml = encodeXMLAttribute(he.getRequestURI().getPath());

	  try {
	    URI path_translated = root_uri.resolve(req_uri_raw.substring(1));
	    File file = new File(path_translated).getCanonicalFile();
	    URI  uri  = file.toURI();

	    if (!file.getAbsolutePath().startsWith(root)) {
	      // Deny access to files outside the root
	      throw new FileNotFoundException("Not Found");
	    }
	    else {
	      File app_file = null;

	      if (file.exists()) {
		if (file.isDirectory()) {

		  // Directory URIs must end with '/', or else the
		  // client will fail to resolve our relative URIs in
		  // the file listing.

		  if (!req_uri_raw.endsWith("/")) {
		    throw new FileNotFoundException("Directory URIs must end with '/'");
		  }

		  StringBuilder sb = new StringBuilder();

		  sb.append(getHTMLHeader(esxx) +
			    "<table summary='Directory Listing of " + req_uri_xml + "'>" +
			    "<caption>Directory Listing of " + req_uri_xml + "</caption>" +
			    "<thead><tr>" +
			    "<td>Name</td>" +
			    "<td>Last Modified</td>" +
			    "<td>Size</td>" +
			    "<td>Type</td>" +
			    "</tr></thead>" +
			    "<tbody>");

		  if (!req_uri_raw.equals("/")) {
		    sb.append("<tr>" +
			      "<td><a href='..'>Parent Directory</a></td>" +
			      "<td>&#160;</td>" +
			      "<td>&#160;</td>" +
			      "<td>&#160;</td>" +
			      "</tr>");
		  }

		  File[] files = file.listFiles();
		  java.util.Arrays.sort(files);

		  for (File f : files) {
		    if (f.isHidden()) {
		      continue;
		    }

		    String p  = f.isDirectory() ? f.getName() + "/" : f.getName();
		    String fp = uri.relativize(f.toURI()).toASCIIString();
		    String d  = isoFormat.format(new Date(f.lastModified()));
		    String l  = f.isDirectory() ? "&#160;" : "" + f.length();
		    String t  = f.isDirectory() ? "Directory" : fileTypeMap.getContentType(f);

		    sb.append("<tr>");
		    sb.append("<td><a href='" + encodeXMLAttribute(fp) + "'>");
		    sb.append(encodeXMLContent(p) + "</a></td>");
		    sb.append("<td>" + d + "</td>");
		    sb.append("<td>" + l + "</td>");
		    sb.append("<td>" + t + "</td>");
		    sb.append("</tr>");
		  }

		  sb.append("</tbody></table>" + getHTMLFooter(esxx));

		  respond(he, 200, "text/html", sb.toString());
		}
		else {
		  if (fileTypeMap.getContentType(file).equals("application/x-esxx+xml")) {
		    app_file = file;
		  }
		  else {
		    he.getResponseHeaders().set("Content-Type", fileTypeMap.getContentType(file));
		    he.sendResponseHeaders(200, file.length());
		    IO.copyStream(new FileInputStream(file), he.getResponseBody());
		  }
		}
	      }
	      else {
		// Find a file that do exists
		app_file = file;
		while (app_file != null && !app_file.exists()) {
		  app_file = app_file.getParentFile();
		}

		if (app_file.isDirectory()) {
		  throw new FileNotFoundException("Not Found");
		}

		if (!fileTypeMap.getContentType(app_file).equals("application/x-esxx+xml")) {
		  throw new FileNotFoundException("Only ESXX files are directories");
		}
	      }

	      if (app_file != null) {
		HTTPRequest hr = new HTTPRequest(root_uri,
						 app_file.toURI(),
						 path_translated,
						 he);
		esxx.addRequest(hr, hr, 0);
		he = null;
	      }
	    }
	  }
	  catch (Exception ex) {
	    int code = 500;
	    String title = "Internal Server Error";

	    if (ex instanceof FileNotFoundException) {
	      code = 404;
	      title = "Not Found";
	    }
	    else {
	      ex.printStackTrace();
	    }

	    respond(he, code, "text/html",
		    getHTMLHeader(esxx) +
		    "<h2>" + title + "</h2>" +
		    "<p>The requested resource " + req_uri_xml + " failed: " +
		    encodeXMLContent(ex.getMessage()) +
		    ".</p>" + getHTMLFooter(esxx));
	  }
	  finally {
	    if (he != null) {
	      he.close();
	    }
	  }
	}
      });

    int http_threads = Integer.parseInt(esxx.settings().getProperty("esxx.http_threads", "0"));

    if (http_threads == 0) {
      // Use an unbounded thread pool
      hs.setExecutor(Executors.newCachedThreadPool());
    }
    else {
      hs.setExecutor(Executors.newFixedThreadPool(http_threads));
    }

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

  private static void respond(HttpExchange he, int status, String ct, String body)
    throws IOException {
    if (ct.startsWith("text/") &&
	!ct.contains("charset")) {
      ct = ct + "; charset=UTF-8";
    }

    he.getResponseHeaders().set("Content-Type", ct);
    he.sendResponseHeaders(status, 0);
    PrintStream ps = new PrintStream(he.getResponseBody(), false, "UTF-8");
    ps.print(body);
    ps.close();
  }

  private static class ESXXFileTypeMap
    extends MimetypesFileTypeMap {
    public ESXXFileTypeMap() {
      super();

      addIfMissing("css",   "text/css");
      addIfMissing("esxx",  "application/x-esxx+xml");
      addIfMissing("gif",   "image/gif");
      addIfMissing("html",  "text/html");
      addIfMissing("jpg",   "image/jpeg");
      addIfMissing("js",    "application/x-javascript");
      addIfMissing("pdf",   "application/pdf");
      addIfMissing("png",   "image/png");
      addIfMissing("txt",   "text/plain");
      addIfMissing("xhtml", "application/xhtml+xml");
      addIfMissing("xml",   "application/xml");
      addIfMissing("xsl",   "text/xsl");
    }

    private void addIfMissing(String ext, String type) {
      if (getContentType("file." + ext).equals("application/octet-stream")) {
	addMimeTypes(type + " " + ext + " " + ext.toUpperCase());
      }
    }
  }

  private HttpExchange httpExchange;
}
