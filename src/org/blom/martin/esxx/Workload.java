
package org.blom.martin.esxx;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.util.Properties;
import java.util.HashMap;

public abstract class Workload {
    public abstract void finished(int rc, Properties headers, Object result);

    public Workload(URL url, Properties properties,
		    InputStream in, Writer error) {
      streamURL       = url;

      this.in         = in;
      this.debug      = new StringWriter();
      this.error      = error;
      this.properties = properties;
    }

    public URL getURL() {
      return streamURL;
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

    private InputStream in;
    private StringWriter debug;
    private Writer error;
    private Properties properties;
};
