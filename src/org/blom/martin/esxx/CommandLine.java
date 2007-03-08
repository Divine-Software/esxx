
package org.blom.martin.esxx;

import java.io.*;
import java.net.*;
import java.util.Properties;

public class CommandLine {
    static private Object mutex = new Object();

    static private WL result;

    static private class WL 
      extends Workload {
	public WL(String file_name)
	  throws MalformedURLException {
	  this(file_name, new ByteArrayOutputStream());
	}

	private WL(String file_name, ByteArrayOutputStream b)
	  throws MalformedURLException {
	  super(new URL("file", "", file_name),
		b, new StringWriter(), new PrintWriter(System.err), 
		System.getProperties(), null);
	  body = b;
	}

	public void finished(int rc, Properties headers) {
	  this.rc = rc;
	  this.headers = headers;

	  synchronized (mutex) {
	    result = this;
	  }
	}

	public int rc;
	public Properties headers;
	public ByteArrayOutputStream body;
    }


    public static void main(String[] args) {
      Properties p = new Properties();

      ESXX esxx = new ESXX(p);

      try {
	WL w = new WL(args[0]);

	esxx.addWorkload(w);

	while (true) {
	  synchronized (mutex) {
	    if (result != null) {
	      break;
	    }
	  }

	  try {
	    Thread.sleep(100);
	  }
	  catch (Exception ex) {
	    ex.printStackTrace();
	  }
	}

//      try {
//	System.err.flush();

// 	result.headers.store(new OutputStreamWriter(System.out), "ESXX Output Headers");
// 	System.out.println("");
	try {
	  w.getErrorWriter().flush();
	  w.getDebugWriter().flush();
	}
	catch (IOException ex) {
	}
	System.out.println(result.body.toString());
	System.exit(result.rc);
      }
      catch (MalformedURLException ex) {
	ex.printStackTrace();
      }
//      }
//       catch (IOException ex) {
// 	ex.printStackTrace();
//       }
    }
};
