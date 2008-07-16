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

import java.io.*;
import java.net.*;
import java.util.Properties;
import org.esxx.*;
import org.esxx.util.IO;
import org.mozilla.javascript.*;

public class WebRequest
  extends Request
  implements ESXX.ResponseHandler {

  public WebRequest(URL url, String[] command_line, Properties properties,
		    InputStream in, OutputStream error, OutputStream out)
    throws IOException {
    super(url, command_line, properties, in, error);
    outStream = out;
  }

  @Override
  public URL getWD() {
    try {
      URI main = super.getURL().toURI();

      return new File(main).getParentFile().toURI().toURL();
    }
    catch (Exception ex) {
      // Should not happen. Fall back to super method if it does.
    }

    return super.getWD();
  }

  public Integer handleResponse(ESXX esxx, Context cx, Response response)
    throws Exception {
    // Output HTTP headers
    final PrintWriter out = new PrintWriter(IO.createWriter(outStream, "US-ASCII"));

    out.println("Status: " + response.getStatus());
    out.println("Content-Type: " + response.getContentType(true));

    response.enumerateHeaders(new Response.HeaderEnumerator() {
	public void header(String name, String value) {
	  out.println(name + ": " + value);
	}
      });

    out.println();
    out.flush();

    response.writeResult(esxx, cx, outStream);

    getErrorWriter().flush();
    getDebugWriter().flush();
    outStream.flush();

    return 0;
  }

  public Integer handleError(ESXX esxx, Context cx, Throwable ex) {
    String title = "ESXX Server Error";
    int    code  = 500;

    if (ex instanceof ESXXException) {
      code = ((ESXXException) ex).getStatus();
    }

    StringWriter sw = new StringWriter();
    PrintWriter out = new PrintWriter(sw);

    out.println(esxx.getHTMLHeader());
    out.println("<h2>" + title + "</h2>");
    out.println("<h3>Unhandled exception: " + ex.getClass().getSimpleName() + "</h3>");
    if (ex instanceof ESXXException ||
	ex instanceof javax.xml.stream.XMLStreamException ||
	ex instanceof javax.xml.transform.TransformerException) {
      out.println("<p><tt>" + encodeXMLContent(ex.getMessage()) + "</tt></p>");
    }
    else if (ex instanceof RhinoException) {
      out.println("<pre>");
      out.println(ex.getClass().getSimpleName() + ": " + encodeXMLContent(ex.getMessage()));
      out.println(((RhinoException) ex).getScriptStackTrace(new ESXX.JSFilenameFilter()));
      out.println("</pre>");
    }
    else {
      out.println("<pre>");
      ex.printStackTrace(out);
      out.println("</pre>");
    }
    out.println(esxx.getHTMLFooter());
    out.close();

    try {
      return handleResponse(esxx, cx,
			    new Response(code, "text/html; charset=UTF-8",
					 sw.toString(), null));
    }
    catch (Exception ex2) {
      // Hmm
      return 20;
    }
  }

  protected static URL createURL(Properties headers)
    throws IOException {
    try {
      File file = new File(headers.getProperty("PATH_TRANSLATED"));

      while (file != null && !file.exists()) {
	file = file.getParentFile();
      }

      if (file.isDirectory()) {
	throw new IOException("Unable to find a file in path "
			      + headers.getProperty("PATH_TRANSLATED"));
      }

      return new URL("file", "", file.getAbsolutePath());
    }
    catch (MalformedURLException ex) {
      ex.printStackTrace();
      return null;
    }
  }

  protected static String encodeXMLContent(String str) {
    if (str == null) {
      return "";
    }

    return str.replaceAll("&", "&amp;").replaceAll("<", "&lt;");
  }

  protected static String encodeXMLAttribute(String str) {
    return encodeXMLContent(str).replaceAll("'", "&apos;").replaceAll("\"", "&quot;");
  }

  private OutputStream outStream;
}
