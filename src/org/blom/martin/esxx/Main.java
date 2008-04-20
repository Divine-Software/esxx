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
import org.apache.commons.cli.*;
import org.bumblescript.jfast.*;

public class Main {
  static private Integer cgiResult = null;
  static private Object cgiMutex = new Object();

  static private class CGIRequest
    extends Request {

    public CGIRequest(Properties cgi)
      throws IOException {
      super(createURL(cgi), null, cgi, 
	    System.in, 
	    new OutputStreamWriter(System.err));
      jFast = null;
      outStream = System.out;
      scriptMode = false;
    }

    public CGIRequest(JFastRequest jfast) 
      throws IOException {
      super(createURL(jfast.properties), null, jfast.properties,
	    new ByteArrayInputStream(jfast.data),
	    new OutputStreamWriter(System.err));
      jFast = jfast;
      outStream = jfast.out;
      scriptMode = false;
    }

    public CGIRequest(URL url, String[] cmdline) 
      throws IOException {
      super(url, cmdline, new Properties(), 
	    System.in, 
	    new OutputStreamWriter(System.err));
      jFast = null;
      outStream = System.out;
      scriptMode = true;
    }

    public void finished(int rc, JSResponse response) {
      try {
	if (!scriptMode) {
	  // Output HTTP headers
	  final PrintWriter out = new PrintWriter(createWriter(outStream, "US-ASCII"));

	  out.println("Status: " + response.getStatus());
	  out.println("Content-Type: " + response.getContentType());
	    
	  response.enumerateHeaders(new JSResponse.HeaderEnumerator() {
	      public void header(String name, String value) {
		out.println(name + ": " + value);
	      }
	    });

	  out.println();
	  out.flush();
	}
	else {
	  // Output debug stream first
	  getErrorWriter().write(getDebugWriter().toString());
	}

	Object result = response.getResult();

	// Output body
	writeResult(result, response.getContentType(), outStream);

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
    }

    private static URL createURL(Properties headers) 
      throws IOException {
      try {
	File file = new File(headers.getProperty("PATH_TRANSLATED"));

	while (file != null && !file.exists()) {
	  file = file.getParentFile();
	}

	if (file.isDirectory()) {
	  throw new IOException("Unable to find a file in path " 
				+ headers.getProperty("PATH_TRANSLATED"));
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
    boolean scriptMode;
  }

  private static void writeResult(Object result, String content_type, OutputStream out) 
    throws IOException {
    if (result instanceof ByteArrayOutputStream) {
      ByteArrayOutputStream bos = (ByteArrayOutputStream) result;

      bos.writeTo(out);
    }
    else if (result instanceof ByteBuffer) {
      // Write result as-is to output stream
      WritableByteChannel wbc = Channels.newChannel(out);
      ByteBuffer          bb  = (ByteBuffer) result;
	      
      bb.rewind();

      while (bb.hasRemaining()) {
	wbc.write(bb);
      }

      wbc.close();
    }
    else if (result instanceof String) {
      // Write result as-is, using the specified charset (if present)
      Writer ow = Request.createWriter(out, content_type);
      ow.write((String) result);
      ow.close();
    }
    else if (result instanceof BufferedImage) {
      // TODO ...
      throw new UnsupportedOperationException("BufferedImage results not supported yet.");
    }
    else {
      throw new UnsupportedOperationException("Unsupported result class type: " + result.getClass());
    }
  }

  private static void usage(Options opt, String error, int rc) {
    PrintWriter  err = new PrintWriter(System.err);
    HelpFormatter hf = new HelpFormatter();

    hf.printUsage(err, 80, "esxx.jar [OPTION...] [--script -- <script.js> SCRIPT ARGS...]");
    hf.printOptions(err, 80, opt, 2, 8);

    if (error != null) {
      err.println();
      hf.printWrapped(err, 80, "Invalid arguments: " + error + ".");
    }

    err.flush();
    System.exit(rc);
  }

  public static void main(String[] args) {
    Options opt = new Options();
    OptionGroup mode_opt = new OptionGroup();

    mode_opt.addOption(new Option("b", "bind",    true, ("Listen for FastCGI requests on " +
							 "this <port>")));
    mode_opt.addOption(new Option("c", "cgi",    false, "Force CGI mode."));
    mode_opt.addOption(new Option("s", "script", false, "Force script mode."));

    opt.addOptionGroup(mode_opt);
    opt.addOption("m", "method",  true,  "Override CGI request method");
    opt.addOption("f", "file",    true,  "Override CGI request file");
    opt.addOption("?", "help",    false, "Show help");

    try {
      CommandLineParser parser = new GnuParser();
      CommandLine cmd = parser.parse(opt, args, false);

      if (!cmd.hasOption('c') && 
	  (cmd.hasOption('m') || cmd.hasOption('f'))) {
	throw new ParseException("--method and --file can only be specified in --cgi mode");
      }

      int fastcgi_port = -1;
      Properties   cgi = null;
      String[]  script = null;

      if (cmd.hasOption('?')) {
	usage(opt, null, 0);
      }

      if (cmd.hasOption('b')) {
	fastcgi_port = Integer.parseInt(cmd.getOptionValue('b'));
      }
      else if (cmd.hasOption('c')) {
	cgi = new Properties();
      }
      else if (cmd.hasOption('s')) {
	script = cmd.getArgs();
      }
      else {
	// Guess execution mode by looking at FCGI_PORT and
	// REQUEST_METHOD environment variables.
	String fcgi_port  = System.getenv("FCGI_PORT");
	String req_method = System.getenv("REQUEST_METHOD");

	if (fcgi_port != null) {
	  fastcgi_port = Integer.parseInt(fcgi_port);
	}
	else if (req_method != null) {
	  cgi = new Properties();
	}
	else {
	  // Default mode is to execute a JS script
	  script = cmd.getArgs();
	}
      }

      ESXX esxx = new ESXX(System.getProperties());

      if (fastcgi_port != -1) {
	JFast jfast = new JFast(fastcgi_port);
	  
	while (true) {
	  try {
	    JFastRequest req = jfast.acceptRequest();

	    // Fire and forget
	    esxx.addRequest(new CGIRequest(req), 0);
	  }
	  catch (JFastException ex) {
	    ex.printStackTrace();
	  }
	  catch (IOException ex) {
	    ex.printStackTrace();
	  }
	}
      }
      else if (cgi != null) {
	cgi.putAll(System.getenv());

	if (cmd.hasOption('m')) {
	  cgi.setProperty("REQUEST_METHOD", cmd.getOptionValue('m'));
	}

	if (cmd.hasOption('f')) {
	  String file = cmd.getOptionValue('f');

	  cgi.setProperty("PATH_TRANSLATED", new File(file).getAbsolutePath());
	  cgi.setProperty("PATH_INFO", file);
	}

	if (cgi.getProperty("REQUEST_METHOD") == null) {
	  usage(opt, "REQUEST_METHOD not set", 10);
	}

	if (cgi.getProperty("PATH_TRANSLATED") == null) {
	  usage(opt, "PATH_TRANSLATED not set", 10);
	}

	ESXX.Workload wl = esxx.addRequest(new CGIRequest(cgi), 0);

	wl.future.get();
	System.exit(cgiResult);
      }
      else if (script != null && script.length != 0) {
	File file = new File(script[0]);
	URL  url  = new URL("file", "", file.getAbsolutePath());

	ESXX.Workload wl = esxx.addRequest(new CGIRequest(url, script), 0);

	wl.future.get();
	System.exit(cgiResult);
      }
      else {
	usage(opt, "Required argument missing", 10);
      }
    }
    catch (ParseException ex) {
      usage(opt, ex.getMessage(), 10);
    }
    catch (IOException ex) {
      System.err.println("I/O error: " + ex.getMessage());
      System.exit(20);
    }
    catch (Exception ex) {
      ex.printStackTrace();
      System.exit(20);
    }
      
    System.exit(0);
  }
};
