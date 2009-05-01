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
    JSGlobal global = app.getJSGlobal();

    try {
      //     if (app.isDebuggerActivated()) {
      //       org.mozilla.javascript.tools.debugger.Main.mainEmbedded(esxx.getContextFactory(),
      // 							      global,
      // 							      app.getAppName());
      //     }

      JSResponse result = null;

      // Create a Request object
      JSRequest jsreq = (JSRequest) JSESXX.newObject(cx, global, "Request", 
						     new Object[] { request });

      try {
	if (request instanceof org.esxx.request.ScriptRequest) {
	  result = app.executeMain(cx, jsreq, request.getCommandLine());
	}
	else if (app.hasHandlers()) {
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
	else {
	  // No handlers; the document is the result

	  result = app.wrapResult(cx, jsreq, app.getMainDocument());
	}
      }
      catch (Exception ex) {
	// On errors, invoke error handler
	result = app.executeErrorHandler(cx, jsreq, ex);
      }

      Response response = result.getResponse();

      response.unwrapResult();

      if (response.getResult() instanceof Node) {
	handleTransformation(request, response, result, app, cx, global);
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

  private void handleTransformation(Request request, Response response,
				    JSResponse js_response, Application app,
				    Context cx, Scriptable scope)
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
      return;
    }


    long start_time = System.currentTimeMillis();
    Stylesheet xslt = esxx.getCachedStylesheet(stylesheet);

    try {
      XsltExecutable  xe = xslt.getExecutable();
      XsltTransformer tr = xe.load();
      ByteArrayOutputStream os = new ByteArrayOutputStream();
      Serializer s = new Serializer();
      s.setOutputStream(os);

      // Remove this code when upgrading to Saxon 9.1 (?)
      Properties op = xe.getUnderlyingCompiledStylesheet().getOutputProperties();
      s.setOutputProperty(BYTE_ORDER_MARK,        op.getProperty("byte-order-mark"));
      s.setOutputProperty(CDATA_SECTION_ELEMENTS, op.getProperty("cdata-section-elements"));
      s.setOutputProperty(DOCTYPE_PUBLIC,         op.getProperty("doctype-public"));
      s.setOutputProperty(DOCTYPE_SYSTEM,         op.getProperty("doctype-system"));
      s.setOutputProperty(ENCODING,               op.getProperty("encoding"));
      s.setOutputProperty(ESCAPE_URI_ATTRIBUTES,  op.getProperty("escape-uri-attributes"));
      s.setOutputProperty(INCLUDE_CONTENT_TYPE,   op.getProperty("include-content-type"));
      s.setOutputProperty(INDENT,                 op.getProperty("indent"));
      s.setOutputProperty(MEDIA_TYPE,             op.getProperty("media-type", content_type));
      s.setOutputProperty(METHOD,                 op.getProperty("method"));
      //    s.setOutputProperty(NORMALIZATION_FORM,     op.getProperty("normalization-form"));
      s.setOutputProperty(OMIT_XML_DECLARATION,   op.getProperty("omit-xml-declaration"));
      s.setOutputProperty(STANDALONE,             op.getProperty("standalone"));
      s.setOutputProperty(UNDECLARE_PREFIXES,     op.getProperty("undeclare-prefixes"));
      s.setOutputProperty(USE_CHARACTER_MAPS,     op.getProperty("use-character-maps"));
      s.setOutputProperty(VERSION,                op.getProperty("version"));

      // This is sad, but Saxon can only transform the DOM Document Element node.
      org.w3c.dom.DOMImplementation di = node.getOwnerDocument().getImplementation();
      Document doc = di.createDocument(null, "dummy", null);
      Node adopted = doc.adoptNode(node);
      if (adopted == null) {
	// Ugh ...
	adopted = doc.importNode(node, true);
      }
      //    doc.appendChild(adopted);
      doc.replaceChild(adopted, doc.getDocumentElement());

      // Append the debug output while we're at it, and let the
      // stylesheet decide if it should be output or not.
      doc.appendChild(doc.createComment(getDebugLogForComment(request)));

      tr.setSource(new DOMSource(doc));
      tr.setDestination(s);

      try {
	// Set stylesheet params
	Scriptable params = js_response.jsGet_params();

	for (Object o : params.getIds()) {
	  if (o instanceof String) {
	    XdmValue xv = javaToXDM(params.get((String) o, params));

	    tr.setParameter(new QName((String) o), xv);
	  }
	}

	// Make current scope available to ESXXExpression and begin transformation
	cx.putThreadLocal(ESXXExpression.class, scope);
	tr.transform();
      }
      finally {
	cx.removeThreadLocal(ESXXExpression.class);
      }

      response.setContentType(s.getOutputProperty(MEDIA_TYPE));
      response.setResult(os);
    }
    finally {
      xslt.logUsage(start_time);
    }
  }

  private XdmValue javaToXDM(Object o) {
    if (o instanceof JSURI) {
      o = ((JSURI) o).jsGet_javaURI();
    }
    else if (o instanceof org.mozilla.javascript.xml.XMLObject) {
      o = ESXX.e4xToDOM((Scriptable) o);
    }

    if (o instanceof java.math.BigDecimal) {
      return new XdmAtomicValue((java.math.BigDecimal) o);
    }
    else if (o instanceof Boolean) {
      return new XdmAtomicValue((Boolean) o);
    }
    else if (o instanceof Double) {
      return new XdmAtomicValue((Double) o);
    }
    else if (o instanceof URI) {
      return new XdmAtomicValue((URI) o);
    }
    else if (o instanceof Node) {
      return esxx.getSaxonDocumentBuilder().wrap((Node) o);
    }
    else {
      return new XdmAtomicValue(Context.toString(o));
    }
  }

  private ESXX esxx;
}
