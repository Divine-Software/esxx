
package org.blom.martin.esxx;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.bumblescript.jfast.*;

public class CommandLine {
    static private Integer cgiResult = null;
    static private Object cgiMutex = new Object();

    static private class CGIWorkload
      extends Workload {

	public CGIWorkload(Properties cgi) {
	  this(cgi, 
	       System.in,
	       new OutputStreamWriter(System.err),
	       System.out);
	}


	public CGIWorkload(JFastRequest jfast) {
	  this(jfast.properties,
	       new ByteArrayInputStream(jfast.data),
	       new OutputStreamWriter(System.err),
	       jfast.out);
	  jFast = jfast;
	}


	private CGIWorkload(Properties   cgi,
			    InputStream  in,
			    Writer       error,
			    OutputStream out_stream) {
	  super(createURL(cgi),
		cgi,
		in, error);
	  outStream  = out_stream;
	}

	public void finished(int rc, Properties headers, Object result) {
	  try {
	    PrintWriter out = new PrintWriter(createWriter(outStream, "US-ASCII"));

	    // Output HTTP headers
	    String content_type = "text/xml";

	    for (Map.Entry<Object, Object> h : headers.entrySet()) {
	      String name  = (String) h.getKey();
	      String value = (String) h.getValue();

	      if (name.equals("Content-Type")) {
		content_type = value;
	      }

	      out.println(name + ": " + value);
	    }

	    out.println("");
	    out.flush();

	    // Output body
	    if (result instanceof ByteArrayOutputStream) {
	      ByteArrayOutputStream bos = (ByteArrayOutputStream) result;

	      bos.writeTo(outStream);
	    }
	    else if (result instanceof ByteBuffer) {
	      // Write result as-is to output stream
	      WritableByteChannel wbc = Channels.newChannel(outStream);
	      ByteBuffer          bb  = (ByteBuffer) result;
	      
	      bb.rewind();

	      while (bb.hasRemaining()) {
		wbc.write(bb);
	      }

	      wbc.close();
	    }
	    else if (result instanceof String) {
	      // Write result as-is, using the specified charset (if present)
	      Writer ow = Workload.createWriter(outStream, content_type);
	      ow.write((String) result);
	      ow.close();
	    }
	    else if (result instanceof BufferedImage) {
	      // TODO ...
	      throw new InternalError("BufferedImage results not supported yet.");
	    }
	    else {
	      throw new InternalError("Unsupported result class type: " + result.getClass());
	    }

	    // Close streams
	    try { getInputStream().close(); } catch (IOException ex) {}
	    try { getErrorWriter().close(); } catch (IOException ex) {}
	    try { getDebugWriter().close(); } catch (IOException ex) {}
	    try { outStream.close();        } catch (IOException ex) {}
	
	    if (jFast == null) {
	      synchronized (cgiMutex) {
		cgiResult = rc;
		cgiMutex.notify();
	      }
	    }
	    else {
	      jFast.end();
	    }
	  }
	  catch (IOException ex) {
	    ex.printStackTrace();
	  }

//	  System.err.println("Request took " + (System.currentTimeMillis() - start) + " ms");
	}
//	private long start = System.currentTimeMillis();

	static URL createURL(Properties headers) {
	  try {
	    File file = new File(headers.getProperty("PATH_TRANSLATED"));

	    while (file != null && !file.exists()) {
	      file = file.getParentFile();
	    }

	    return new URL("file", "", file.getAbsolutePath());
	  }
	  catch (MalformedURLException ex) {
	    ex.printStackTrace();
	    return null;
	  }
	}

	private OutputStream outStream;
	private JFastRequest jFast;
    }


    public static void main(String[] args) {
      try {
	Properties settings = new Properties();

	JFast fastcgi  = null;
	Properties cgi = null;

	try {
	  fastcgi = new JFast(Integer.parseInt(System.getenv().get("FCGI_PORT")));
	}
	catch (Exception ex) {
	  // FCGI not available, try plain CGI

	  if (System.getenv().get("REQUEST_METHOD") != null) {
	    cgi = new Properties();
	    cgi.putAll(System.getenv());
	  }
	  else {
	    // CGI is not available either, use command line
	    cgi = new Properties();
	  
	    cgi.setProperty("REQUEST_METHOD", args[0]);
	    cgi.setProperty("PATH_TRANSLATED", new File(args[1]).getAbsolutePath());
	    cgi.setProperty("PATH_INFO", args[1]);
	  }
	}

	ESXX esxx = new ESXX(settings);

	if (fastcgi != null) {
	  while (true) {
	    try {
	      JFastRequest req = fastcgi.acceptRequest();

	      esxx.addWorkload(new CGIWorkload(req));
	    }
	    catch (JFastException ex) {
	      ex.printStackTrace();
	    }
	    catch (IOException ex) {
	      ex.printStackTrace();
	      System.exit(10);
	    }
	  }
	}
	else {
	  esxx.addWorkload(new CGIWorkload(cgi));
	
	  synchronized (cgiMutex) {
	    while (cgiResult == null) {
	      try {
		cgiMutex.wait();
	      }
	      catch (InterruptedException ex) {
	      }
	    }
	  }

	  System.exit(cgiResult);
	}
      }
      catch (Exception ex) {
	ex.printStackTrace();
      }
    }
};
