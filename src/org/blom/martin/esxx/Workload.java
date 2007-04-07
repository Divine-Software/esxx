
package org.blom.martin.esxx;

import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.util.Properties;

public abstract class Workload {
    public abstract void finished(int rc, Properties headers);

    public Workload(URL url, Properties properties,
		    InputStream in, Writer out,
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

//     public Reader getInReader() {
//       return in;
//     }

    public InputStream getInputStream() {
      return in;
    }

    public Writer getOutWriter() {
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

    private URL streamURL;

    private InputStream in;
    private Writer out;
    private Writer debug;
    private Writer error;
    private Properties properties;
};
