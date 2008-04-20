/*
     ESXX - The friendly ECMAscript/XML Application Server
     Copyright (C) 2007 Martin Blom <martin@blom.org>
     
     This program is free software; you can redistribute it and/or
     modify it under the terms of the GNU General Public License
     as published by the Free Software Foundation; either version 2
     of the License, or (at your option) any later version.
     
     This program is distributed in the hope that it will be useful,
     but WITHOUT ANY WARRANTY; without even the implied warranty of
     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
     GNU General Public License for more details.
     
     You should have received a copy of the GNU General Public License
     along with this program; if not, write to the Free Software
     Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
*/

package org.blom.martin.esxx;

import org.blom.martin.esxx.js.JSResponse;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.util.HashMap;
import java.util.Properties;

public abstract class Request {
    public abstract void finished(int rc, JSResponse response);

    public Request(URL url, String[] command_line, Properties properties,
		   InputStream in, Writer error) 
      throws IOException {
      streamURL       = url;
      this.args       = command_line;
      this.in         = in;
      this.debug      = new StringWriter();
      this.error      = error;
      this.properties = properties;

      try {
	workingDirectory = new File("").toURI().toURL();
      }
      catch (java.net.MalformedURLException ex) {
	throw new IOException("Unable to get current working directory as an URI: " 
			      + ex.getMessage(), ex);
      }
    }

    public URL getURL() {
      return streamURL;
    }

    public URL getWD() {
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
      return error;
    }

    public Properties getProperties() {
      return properties;
    }

    public static Reader createReader(InputStream is, String content_type) 
      throws java.io.UnsupportedEncodingException {
      HashMap<String, String> params = new HashMap<String, String>();
      String                  ct = ESXX.parseMIMEType(content_type, params);
      String                  cs = params.get("charset");

      if (cs == null) {
	cs = java.nio.charset.Charset.defaultCharset().name();
      }

      return new InputStreamReader(is, cs);
    }

    public static Writer createWriter(OutputStream os, String content_type)
      throws java.io.UnsupportedEncodingException {
      HashMap<String, String> params = new HashMap<String, String>();
      String                  ct = ESXX.parseMIMEType(content_type, params);
      String                  cs = params.get("charset");

      if (cs == null) {
	cs = java.nio.charset.Charset.defaultCharset().name();
      }

      return new OutputStreamWriter(os, cs);
    }

    private URL streamURL;
    private URL workingDirectory;
    private String[] args;
    private InputStream in;
    private StringWriter debug;
    private Writer error;
    private Properties properties;
};
