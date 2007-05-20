
package org.blom.martin.esxx;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.util.Properties;

public abstract class Workload {
    public abstract void finished(int rc, Properties headers);

    public Workload(URL url, Properties properties,
		    InputStream in, OutputStream out,
		    Writer debug, Writer error) {
      streamURL       = url;

      this.in         = in;
      this.out        = out;
      this.debug      = debug;
      this.error      = error;
      this.properties = properties;
    }

    public URL getURL() {
      return streamURL;
    }

    public InputStream getInputStream() {
      return in;
    }

    public OutputStream getOutputStream() {
      return out;
    }

    public Writer getDebugWriter() {
      return debug;
    }

    public Writer getErrorWriter() {
      return error;
    }

    public Properties getProperties() {
      return properties;
    }

    public static Reader createReader(InputStream is, String content_type) {
      // TODO: Read charset
      return new InputStreamReader(is);
    }

    public static Writer createWriter(OutputStream os, String content_type) {
      // TODO: Read charset
      return new OutputStreamWriter(os);
    }

    private URL streamURL;

    private InputStream in;
    private OutputStream out;
    private Writer debug;
    private Writer error;
    private Properties properties;
};
