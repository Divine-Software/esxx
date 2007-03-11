
package org.blom.martin.esxx;

import java.io.*;
import java.net.*;
import java.util.Properties;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.bumblescript.jfast.*;

public class CommandLine {
    static private Integer cgiResult = null;
    static private Object cgiMutex = new Object();

    static private class CGIWorkload
      extends Workload {

	public CGIWorkload(Properties cgi)
	  throws MalformedURLException {
	  this(cgi, 
	       createReader(System.in, cgi), 
	       new StringWriter(), 
	       new StringWriter(), 
	       new OutputStreamWriter(System.err),
	       System.out);
	}


	public CGIWorkload(JFastRequest jfast)
	  throws MalformedURLException {
	  this(jfast.properties,
	       createReader(new ByteArrayInputStream(jfast.data), jfast.properties),
	       new StringWriter(), 
	       new StringWriter(), 
	       new OutputStreamWriter(System.err),
	       jfast.out);
	  jFast = jfast;
	}


	private CGIWorkload(Properties   cgi,
			    Reader       in,
			    StringWriter body,
			    StringWriter debug, 
			    Writer       error,
			    OutputStream out_stream)
	  throws MalformedURLException {
	  super(new URL("file", "", cgi.getProperty("PATH_TRANSLATED", "default.esxx")),
		cgi,
		in, body, debug, error);
	  this.body  = body;
	  this.debug = debug;
	  outStream  = out_stream;
	}


	public void finished(int rc, Properties headers) {
	  try {
	    getErrorWriter().flush();
	    getDebugWriter().flush();

	    PrintWriter out = createWriter(outStream, headers);

	    // Output HTTP headers

	    for (Map.Entry<Object, Object> h : headers.entrySet()) {
	      out.println(h.getKey() + ": " + h.getValue());
	    }

	    // Output body
	    out.println("");
	    out.println(body);

	    // Output debug log as XML comment, if non-empty.
	    String dstr = debug.toString();
	    
	    if (dstr.length() != 0) {
	      out.println("<!-- Start ESXX Debug Log");
	      out.println(dstr.replaceAll("-->", "--\u00bb"));
	      out.println("End ESXX Debug Log -->");
	    }

	    out.flush();
	
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

	}

	static Reader createReader(InputStream is, Properties headers) {
	  return new InputStreamReader(is);
	}

	static PrintWriter createWriter(OutputStream os, Properties headers) {
	  return new PrintWriter(os);
	}

	private StringWriter body;
	private StringWriter debug;
	private OutputStream outStream;

	JFastRequest jFast;
    }


    public static void main(String[] args) {
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
	}
      }

      ESXX esxx = new ESXX(settings);

      if (fastcgi != null) {
	while (true) {
	  try {
	    JFastRequest req = fastcgi.acceptRequest();

	    esxx.addWorkload(new CGIWorkload(req));
	  }
	  catch (MalformedURLException ex) {
	    ex.printStackTrace();
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
	try {
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
	catch (MalformedURLException ex) {
	  ex.printStackTrace();
	}
      }
    }
};
