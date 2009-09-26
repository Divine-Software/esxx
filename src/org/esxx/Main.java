/*
     ESXX - The friendly ECMAscript/XML Application Server
     Copyright (C) 2007-2008 Martin Blom <martin@blom.org>

     This program is free software: you can redistribute it and/or
     modify it under the terms of the GNU General Public License
     as published by the Free Software Foundation, either version 3
     of the License, or (at your option) any later version.

     This program is distributed in the hope that it will be useful,
     but WITHOUT ANY WARRANTY; without even the implied warranty of
     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
     GNU General Public License for more details.

     You should have received a copy of the GNU General Public License
     along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.esxx;

import java.io.*;
import java.net.URI;
import org.apache.commons.cli.*;
import org.esxx.request.*;

public class Main {
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

    // (Remember: -u/--user, -p/--pidfile and -j/jvmargs are used by the wrapper script)
    mode_opt.addOption(new Option("b", "bind",    true, ("Listen for FastCGI requests on " +
							 "this <port>")));
    mode_opt.addOption(new Option("A", "ajp",     true, ("Listen for AJP13 requests on " +
							 "this <port>")));
    mode_opt.addOption(new Option("H", "http",    true, ("Listen for HTTP requests on " +
							 "this <port>")));
    mode_opt.addOption(new Option("s", "script", false, "Force script mode."));
    mode_opt.addOption(new Option(null,"db-console", false, "Open H2's database console."));

    opt.addOptionGroup(mode_opt);
    opt.addOption("n", "no-handler",      true,  "FCGI requests are direct, without extra handler");
    opt.addOption("r", "http-root",       true,  "Set HTTP root directory or file");
//     opt.addOption("d", "enable-debugger", false, "Enable esxx.debug()");
//     opt.addOption("D", "start-debugger",  false, "Start debugger");
    opt.addOption("?", "help",            false, "Show help");

    try {
      CommandLineParser parser = new GnuParser();
      CommandLine cmd = parser.parse(opt, args, false);

      int fastcgi_port = -1;
      int     ajp_port = -1;
      int    http_port = -1;
      String[]  script = null;

      if (cmd.hasOption('?')) {
	usage(opt, null, 0);
      }

      if (cmd.hasOption('b')) {
	fastcgi_port = Integer.parseInt(cmd.getOptionValue('b'));
      }
      else if (cmd.hasOption('A')) {
	ajp_port = Integer.parseInt(cmd.getOptionValue('A'));
      }
      else if (cmd.hasOption('H')) {
	http_port = Integer.parseInt(cmd.getOptionValue('H'));
      }
      else if (cmd.hasOption('s')) {
	script = cmd.getArgs();
      }
      else if (cmd.hasOption("db-console")) {
	org.h2.tools.Console.main(cmd.getArgs());
	return;
      }
      else {
	// Guess execution mode by looking at FCGI_PORT 
	String fcgi_port  = System.getenv("FCGI_PORT");

	if (fcgi_port != null) {
	  fastcgi_port = Integer.parseInt(fcgi_port);
	}
	else {
	  // Default mode is to execute a JS script
	  script = cmd.getArgs();
	}
      }

      if (script != null) {
	// "Never" unload Applications in script mode
	System.getProperties().setProperty("esxx.cache.apps.max_age", 
					   Long.toString(3600 * 24 * 365 * 10 /* 10 years */));
      }

      ESXX esxx = ESXX.initInstance(System.getProperties(), null);

      esxx.setNoHandlerMode(cmd.getOptionValue('n', "lighttpd.*"));

      // Install our ResponseCache implementation
//       java.net.ResponseCache.setDefault(new org.esxx.cache.DBResponseCache("/tmp/ESXX.WebCache", 
// 									   Integer.MAX_VALUE,
// 									   Long.MAX_VALUE, 
// 									   Long.MAX_VALUE));

      if (fastcgi_port != -1) {
	FCGIRequest.runServer(fastcgi_port);
      }
      else if (ajp_port != -1) {
	Jetty.runJettyServer(-1, ajp_port, URI.create("file:/"));
      }
      else if (http_port != -1) {
	URI fs_root_uri = new File(cmd.getOptionValue('r', "")).getAbsoluteFile().toURI();

	Jetty.runJettyServer(http_port, -1, fs_root_uri);
      }
      else if (script != null && script.length != 0) {
	File file = new File(script[0]);

	ScriptRequest sr = new ScriptRequest();
	sr.initRequest(file.toURI(), script);
	ESXX.Workload wl = esxx.addRequest(sr, sr, -1 /* no timeout for scripts */);

	try {
	  System.exit((Integer) wl.future.get());
	}
	catch (java.util.concurrent.CancellationException ex) {
	  System.exit(5);
	}
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
}
