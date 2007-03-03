
package org.blom.martin.esxx;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;

public abstract class Workload {
    public abstract void finished(int rc, Properties headers);

    public Workload(InputStream code, URL url, 
		    PrintStream out, PrintStream error,
		    Properties properties, byte[] data) {
      inputStream  = new BufferedInputStream(code);
      streamURL    = url;
      lastModified = System.currentTimeMillis();

      this.out        = out;
      this.error      = error;
      this.properties = properties;
      this.data       = data;
    }


    public Workload(File code,
		    PrintStream out, PrintStream error,
		    Properties properties, byte[] data) {
      try {
	file         = code;
	streamURL    = new URL("file", "", file.getAbsolutePath());
	lastModified = file.lastModified();

	this.out        = out;
	this.error      = error;
	this.properties = properties;
	this.data       = data;
      }
      catch (MalformedURLException ex) {
	// This should never happen
	ex.printStackTrace();
	assert false;
      }
    }


    public Workload(URLConnection code,
		    PrintStream out, PrintStream error,
		    Properties properties, byte[] data) {
      urlConnection = code;
      streamURL     = urlConnection.getURL();
      lastModified  = urlConnection.getLastModified();

      this.out        = out;
      this.error      = error;
      this.properties = properties;
      this.data       = data;
    }


    public URL getURL() {
      return streamURL;
    }

    public BufferedInputStream getInputStream()
      throws IOException {
      if (inputStream == null) {
	// The input stream is created here rather than in the
	// constructor so that the 

	if (file != null) {
	  inputStream = new BufferedInputStream(new FileInputStream(file));
	}
	else if (urlConnection != null) {
	  inputStream = new BufferedInputStream(urlConnection.getInputStream());
	}
      }

      assert (inputStream != null);

      return inputStream;
    }


    public long getLastModified() {
      return lastModified;
    }


    public PrintStream getOutStream() {
      return out;
    }


    public PrintStream getErrorStream() {
      return error;
    }


    public Properties getProperties() {
      return properties;
    }

    public byte[] getData() {
      return data;
    }


    private BufferedInputStream inputStream;
    private File file;
    private URLConnection urlConnection;

    private URL streamURL;
    private long lastModified;

    private PrintStream out;
    private PrintStream error;
    private Properties properties;
    private byte data[];
};
