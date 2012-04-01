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

package org.esxx;

import org.esxx.js.*;
import org.esxx.saxon.ESXXExpression;

import java.io.*;
import java.net.URI;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;
import javax.xml.transform.dom.*;
import org.mozilla.javascript.*;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import net.sf.saxon.s9api.*;
import static net.sf.saxon.s9api.Serializer.Property.*;

class Worker {
  public Worker(ESXX esxx) {
    this.esxx = esxx;
  }

  public Response handleRequest(Context cx, Request request)
    throws Exception {
    long start_time = System.currentTimeMillis();
    Application app = esxx.getCachedApplication(cx, request);

    try {
      //     if (app.isDebuggerActivated()) {
      //       org.mozilla.javascript.tools.debugger.Main.mainEmbedded(esxx.getContextFactory(),
      // 							      global,
      // 							      app.getAppName());
      //     }

      // Create a Request object
      JSRequest jsreq = (JSRequest) JSESXX.newObject(cx, app.getJSGlobal(), "Request", 
						     new Object[] { request });
      JSResponse result = null;

      try {
	result = app.executeInitOnce(cx, jsreq);

	if (app.hasHandlers()) {
	  // Execute the SOAP or HTTP handler (if available)
	  String request_method = request.getRequestMethod();
	  String soap_action    = jsreq.jsGet_soapAction();

	  if ("POST".equals(request_method) &&
	      soap_action != null && 
	      app.hasSOAPHandlers()) {
	    result = app.executeSOAPAction(cx, jsreq, soap_action, request.getPathInfo());
	  }
	  else {
	    result = app.executeHTTPMethod(cx, jsreq, request_method, request.getPathInfo());
	  }
	}
	else if (result == null) {
	  // No handlers; the document is the result

	  result = app.wrapResult(cx, app.getMainDocument());
	}
      }
      catch (InterruptedException ex) {
	ESXX.checkTimeout(cx); // Throws TimeOut
	throw ex;
      }
      catch (Exception ex) {
	// On errors, invoke error handler
	result = app.executeErrorHandler(cx, jsreq, ex);
      }

      Response response = result.getResponse();

      response.unwrapResult();

      if (response.getResult() instanceof Node) {
	try {
	  handleTransformation(cx, request, response, result, app);
	}
	catch (Exception ex) {
	  // Invoke error handler on XSLT errors as well
	  response = app.executeErrorHandler(cx, jsreq, ex).getResponse();

	  response.unwrapResult();

	  if (response.getResult() instanceof Node) {
	    handleTransformation(cx, request, response, result, app);
	  }
	}
      }

      // Return response
      return response;
    }
    finally {
      esxx.releaseApplication(app, start_time);
    }
  }

  private static String getDebugLogForComment(Request request) {
    return "Start ESXX Request Log\n"
      + request.getLogAsString().replaceAll("--", "\u2012\u2012")
      + "End ESXX Request Log";
  }

  private void handleTransformation(Context cx, Request request, Response response,
				    JSResponse js_response, Application app)
    throws IOException, SaxonApiException {
    ESXX           esxx = ESXX.getInstance();
    String content_type = response.getContentType(true);
    Node           node = (Node) response.getResult();
    String         ct   = ESXX.parseMIMEType(content_type, null);

    URI stylesheet = app.getStylesheet(cx, ct, request.getPathInfo());

    if (stylesheet == null) {
      // Just serialize and attach debug log
      response.setResult(esxx.serializeNode(node) +
			 "<!--" + getDebugLogForComment(request) + "-->");
      response.setContentType(content_type);
    }
    else {
      Map<QName, Object> params = new HashMap<QName, Object>();
      Scriptable jsparams = js_response.jsGet_params();

      for (Object o : jsparams.getIds()) {
	if (o instanceof String) {
	  params.put(new QName((String) o), jsparams.get((String) o, jsparams));
	}
      }

      ByteArrayOutputStream os = new ByteArrayOutputStream();
      Serializer s = new Serializer();
      s.setOutputStream(os);

      Stylesheet.transform(cx, app.getJSGlobal(), 
			   esxx.getCachedStylesheet(stylesheet, null), params,
			   node, true, getDebugLogForComment(request),
			   s);

      response.setContentType(ESXX.coalesce(s.getOutputProperty(MEDIA_TYPE), content_type));
      response.setResult(os);
    }
  }

  private ESXX esxx;
}
