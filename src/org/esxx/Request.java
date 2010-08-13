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

import java.io.*;
import java.net.URI;
import java.util.Properties;
import java.util.logging.*;
import org.esxx.util.TrivialFormatter;
import org.mozilla.javascript.Context;

public abstract class Request {
    public Request(InputStream in, OutputStream error) {
      this.in         = in;
      this.error      = error;
      this.errorWriter = new OutputStreamWriter(error);
    }

    protected void initRequest(String request_method,
			       URI request_uri,
			       URI script_uri,
			       String path_info,
			       URI script_filename,
			       URI working_directory,
			       Properties cgi_env,
			       Response quick_response) {
      requestMethod    = request_method;
      requestURI       = request_uri;
      scriptURI        = script_uri;
      pathInfo         = path_info;
      scriptFilename   = script_filename;
      workingDirectory = working_directory;
      cgiEnvironment   = cgi_env;
      quickResponse    = quick_response;
    }

    public String getRequestMethod() {
      return requestMethod;
    }

    public URI getRequestURI() {
      return requestURI;
    }

    public URI getScriptURI() {
      return scriptURI;
    }

    public URI getScriptFilename() {
      return scriptFilename;
    }

    public String getScriptName() {
      return scriptURI != null ? scriptURI.getPath() : null;
    }

    public String getPathInfo() {
      return pathInfo;
    }

    public URI getWD() {
      return workingDirectory;
    }

    public InputStream getInputStream() {
      return in;
    }

    public Writer getErrorWriter() {
      return errorWriter;
    }

    public Properties getProperties() {
      return cgiEnvironment;
    }

    public Response getQuickResponse() {
      return quickResponse;
    }

    public Handler getHandler() {
      return null;
    }

    public synchronized Logger getReqLogger() {
      if (logger == null) {
	if (formatter == null) {
	  formatter = new TrivialFormatter(true);
	}

	logger = Logger.getAnonymousLogger();
	localLog = new ByteArrayOutputStream();

	logger.setUseParentHandlers(false);
	logger.addHandler(new ErrorHandler(error, formatter));
	logger.addHandler(new ErrorHandler(localLog, formatter));
      }

      return logger;
    }

    public String getLogAsString() {
      if (localLog != null) {
	return localLog.toString();
      }
      else {
	return "";
      }
    }

    @Override public String toString() {
      return "[Request: " + requestMethod + " " + requestURI + "]";
    }

    public interface Handler {
      public Object handleRequest(Context cx, Request req, Application app)
	throws Exception;
    }

    private Logger logger;
    private ByteArrayOutputStream localLog;
    private static Formatter formatter;

    private class ErrorHandler 
      extends StreamHandler {
      ErrorHandler(OutputStream os, Formatter formatter) {
	super(os, formatter);
	try {
	  setLevel(Level.ALL);
	}
	catch (SecurityException ex) {
	  // Probably a Google App Engine problem
	}
      }

      @Override public void publish(LogRecord record) {
	super.publish(record);
	flush();
      }
    }

    private InputStream in;
    private OutputStream error;
    private Writer errorWriter;

    private String requestMethod;
    private URI requestURI;
    private URI scriptURI;
    private URI scriptFilename;
    private String pathInfo;
    private URI workingDirectory;
    private Properties cgiEnvironment;
    private Response quickResponse;
}
