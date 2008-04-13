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

    static private class CGIRequest
      extends Request {

	public CGIRequest(Properties cgi) {
	  this(cgi, 
	       System.in,
	       new OutputStreamWriter(System.err),
	       System.out);
	}


	public CGIRequest(JFastRequest jfast) {
	  this(jfast.properties,
	       new ByteArrayInputStream(jfast.data),
	       new OutputStreamWriter(System.err),
	       jfast.out);
	  jFast = jfast;
	}


	private CGIRequest(Properties   cgi,
			    InputStream  in,
			    Writer       error,
			    OutputStream out_stream) {
	  super(createURL(cgi),
		cgi,
		in, error);
	  outStream  = out_stream;
	}

	public void finished(int rc, JSResponse response) {
	  try {

	    // Output HTTP headers
	    final PrintWriter out = new PrintWriter(createWriter(outStream, "US-ASCII"));

	    out.println("Status: " + response.getStatus());
	    out.println("Content-Type: " + response.getContentType());
	    
	    response.enumerateHeaders(new JSResponse.HeaderEnumerator() {
		  public void header(String name, String value) {
		    out.println(name + ": " + value);
		  }
	      });

// 	    String content_type = "text/xml";

// 	    for (Map.Entry<Object, Object> h : headers.entrySet()) {
// 	      String name  = (String) h.getKey();
// 	      String value = (String) h.getValue();

// 	      if (name.equals("Content-Type")) {
// 		content_type = value;
// 	      }

// 	      out.println(name + ": " + value);
// 	    }

	    out.println("");
	    out.flush();

	    Object result = response.getResult();

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
	      Writer ow = Request.createWriter(outStream, response.getContentType());
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
//	    try { getInputStream().flush(); } catch (IOException ex) {}
// 	    try { getErrorWriter().flush(); } catch (IOException ex) {}
// 	    try { getDebugWriter().flush(); } catch (IOException ex) {}
// 	    try { outStream.flush();        } catch (IOException ex) {}
	    getErrorWriter().flush();
	    getDebugWriter().flush();
	    outStream.flush();

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
	  cgi = new Properties();
	  cgi.putAll(System.getenv());

	  if (System.getenv("REQUEST_METHOD") == null) {
	    // CGI is not available either, use command line
	    cgi = new Properties();
	    cgi.putAll(System.getenv());
	  
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

	      esxx.addRequest(new CGIRequest(req), 0);
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
	  esxx.addRequest(new CGIRequest(cgi), 0);
	
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
