
package org.blom.martin.esxx;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;

public abstract class Workload {
    public abstract void finished(int rc, Properties headers);

    public Workload(URL url, 
		    OutputStream out, Writer debug, Writer error,
		    Properties properties, byte[] data) {
      streamURL    = url;

      this.out        = out;
      this.debug      = debug;
      this.error      = error;
      this.properties = properties;
      this.data       = data;
    }

    public URL getURL() {
      return streamURL;
    }

    public OutputStream getOutStream() {
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

    public byte[] getData() {
      return data;
    }

    private URL streamURL;

    private OutputStream out;
    private Writer debug;
    private Writer error;
    private Properties properties;
    private byte data[];
};
