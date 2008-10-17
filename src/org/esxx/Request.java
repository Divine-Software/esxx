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

public abstract class Request {
    public Request(URI script_filename, String[] command_line, Properties properties,
		   InputStream in, OutputStream error)
      throws IOException {
      scriptFilename  = script_filename;
      this.args       = command_line != null ? command_line : new String[] {};
      this.in         = in;
      this.debug      = new StringWriter();
      this.error      = error;
      this.errorWriter = new OutputStreamWriter(error);
      this.properties = properties;

      workingDirectory = new File("").toURI();

      String protocol = properties.getProperty("HTTPS", "off").equals("on") ? "https" : "http";
      String hostname = properties.getProperty("HTTP_HOST", "localhost");
      String querystr = properties.getProperty("QUERY_STRING", "");

      String path_translated = properties.getProperty("PATH_TRANSLATED", scriptFilename.getPath());
      String request_uri     = properties.getProperty("REQUEST_URI");

      if (request_uri == null) {
	// Fall back to PATH_INFO (it might work too)
	request_uri = properties.getProperty("PATH_INFO", "");
      }

      try {
	URI pt_uri = new URI("file", null, path_translated, null).normalize();
	
	pathInfo = script_filename.relativize(pt_uri).toString();
	request_uri = new URI(request_uri).normalize().toString();

	if (request_uri.endsWith(pathInfo)) {
	  scriptName = request_uri.substring(0, request_uri.length() - pathInfo.length());

	  if (!scriptName.endsWith("/")) {
	    // Always terminate scriptname with a slash to make it easy
	    // to resolve subresources.
	    scriptName = scriptName + "/";
	  }

	  // Create the absolute URI version of scriptName
	  scriptURI = new URI(protocol, hostname, scriptName, null, null);
	}

	// Create the absolute URI version of request_uri
	requestURI = new URI(protocol, hostname, request_uri, 
			     (querystr.length() == 0 ? null : querystr), null);

	// pathInfo always begins with a slash
	pathInfo = "/" + pathInfo;
      } 
      catch (java.net.URISyntaxException ex) {
	ex.printStackTrace();
	throw new IOException("Failed to construct Request: " + ex.getMessage(), ex);
      }
    }

    public void enableDebugger(boolean enabled) {
      debuggerEnabled = enabled;
    }

    public void activateDebugger(boolean activated) {
      debuggerActivated = activated;
    }

    public boolean isDebuggerEnabled() {
      return debuggerEnabled;
    }

    public boolean isDebuggerActivated() {
      return debuggerActivated;
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
      return scriptName;
    }

    public String getPathInfo() {
      return pathInfo;
    }

    public URI getWD() {
      return workingDirectory;
    }

    public String[] getCommandLine() {
      return args;
    }

    public InputStream getInputStream() {
      return in;
    }

    public StringWriter getDebugWriter() {
      return debug;
    }

    public Writer getErrorWriter() {
      return errorWriter;
    }

    public Properties getProperties() {
      return properties;
    }

    public synchronized Logger getLogger() {
      if (logger == null) {
	if (formatter == null) {
	  formatter = new TrivialFormatter();
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

    private Logger logger;
    private ByteArrayOutputStream localLog;
    private static Formatter formatter;

    private class ErrorHandler 
      extends StreamHandler {
      ErrorHandler(OutputStream os, Formatter formatter) {
	super(os, formatter);
	setLevel(Level.ALL);
      }

      @Override public void publish(LogRecord record) {
	super.publish(record);
	flush();
      }
    }

    private URI requestURI;
    private URI scriptURI;
    private URI scriptFilename;
    private String scriptName;
    private String pathInfo;
    private URI workingDirectory;
    private String[] args;
    private InputStream in;
    private StringWriter debug;
    private OutputStream error;
    private Writer errorWriter;
    private Properties properties;
    private boolean debuggerEnabled;
    private boolean debuggerActivated;
};
