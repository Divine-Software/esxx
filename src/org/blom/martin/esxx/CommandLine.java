
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

	public CGIWorkload(Properties cgi) {
	  this(cgi, 
//	       createReader(System.in, cgi), 
	       System.in,
	       new StringWriter(), 
	       new StringWriter(), 
	       new OutputStreamWriter(System.err),
	       System.out);
	}


	public CGIWorkload(JFastRequest jfast) {
	  this(jfast.properties,
//	       createReader(new ByteArrayInputStream(jfast.data), jfast.properties),
	       new ByteArrayInputStream(jfast.data),
	       new StringWriter(), 
	       new StringWriter(), 
	       new OutputStreamWriter(System.err),
	       jfast.out);
	  jFast = jfast;
	}


	private CGIWorkload(Properties   cgi,
//			    Reader       in,
			    InputStream  in,
			    StringWriter body,
			    StringWriter debug, 
			    Writer       error,
			    OutputStream out_stream) {
	  super(createURL(cgi),
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
	      String name  = (String) h.getKey();
	      String value = (String) h.getValue();

	      if (name.equals("Content-Type")) {
		value = value + "; charset=" +  java.nio.charset.Charset.defaultCharset().name();
	      }

	      out.println(name + ": " + value);
	    }

	    // Output body
	    out.println("");
	    out.println(body);

	    // Output debug log as XML comment, if non-empty.
	    String dstr = debug.toString();
	    
	    if (dstr.length() != 0) {
	      out.println("<!-- Start ESXX Debug Log");
	      out.print(dstr.replaceAll("-->", "--\u00bb"));
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
	  return new PrintWriter(new OutputStreamWriter(os));
	}

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

	private StringWriter body;
	private StringWriter debug;
	private OutputStream outStream;

	JFastRequest jFast;
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
