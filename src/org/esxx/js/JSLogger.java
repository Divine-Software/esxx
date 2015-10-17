/*
     ESXX - The friendly ECMAscript/XML Application Server
     Copyright (C) 2007-2015 Martin Blom <martin@blom.org>

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

package org.esxx.js;

import org.esxx.Application;
import org.esxx.Request;
import java.util.logging.*;
import org.mozilla.javascript.*;

public class JSLogger
  extends ScriptableObject {
  private static final long serialVersionUID = -6547957908255929015L;
  public JSLogger() {
    super();
  }

  public JSLogger(Application app, Request request, Logger logger, String ident) {
    super();

    this.app       = app;
    this.req       = request;
    this.logger    = logger;
    this.ident     = ident;
    this.lastLevel = null;
  }

  public static JSLogger newJSLogger(Context cx, Application app) {
    return (JSLogger) JSESXX.newObject(cx, app.getJSGlobal(), "Logger",
				       new Object[] { app, app.getAppName() });
  }

  public static Object jsConstructor(Context cx,
				     java.lang.Object[] args,
				     Function ctorObj,
				     boolean inNewExpr) {
    Application app = null;
    Request     req = null;
    Logger   logger = null;
    String    ident = null;

    if (args.length >= 1) {
      if (args[0] instanceof Application) {
	app = (Application) args[0];
      }
      else if (args[0] instanceof Request) {
	req = (Request) args[0];
      }
      else if (args[0] instanceof Logger) {
	logger = (Logger) args[0];
      }
      else {
	throw Context.reportRuntimeError("Invalid first argument");
      }
    }
    else {
      throw Context.reportRuntimeError("Missing argument");
    }

    if (args.length >= 2 && args[1] != Context.getUndefinedValue()) {
      ident = Context.toString(args[1]);
    }

    return new JSLogger(app, req, logger, ident);
  }

  public static void finishInit(Scriptable scope, 
				FunctionObject constructor,
				Scriptable prototype) {
    // Create and make the "level" property in the prototype visible
    ScriptableObject.defineProperty(prototype, "level", "debug", ScriptableObject.PERMANENT);
  }


  @Override
  public String getClassName() {
    return "Logger";
  }

  public void jsFunction_debug(String msg) {
    log(Level.FINE, msg);
  }

  public void jsFunction_info(String msg) {
    log(Level.INFO, msg);
  }

  public void jsFunction_warn(String msg) {
    log(Level.WARNING, msg);
  }

  public void jsFunction_error(String msg) {
    log(Level.SEVERE, msg);
  }

  private synchronized void log(Level level, String msg) {
    if (logger == null) {
      if (app != null) {
	logger = app.getAppLogger();
      }
      else if (req != null) {
	logger = req.getReqLogger();
      }
      else {
	throw new IllegalStateException("Expected non-null Application or Request object");
      }
    }

    Object new_level_obj = ScriptableObject.getProperty(this, "level");

    if (new_level_obj != lastLevel) {
      String new_level = Context.toString(new_level_obj);

      if ("debug".equals(new_level)) {
	logger.setLevel(Level.FINE);
      }
      else if ("info".equals(new_level)) {
	logger.setLevel(Level.INFO);
      }
      else if ("warn".equals(new_level)) {
	logger.setLevel(Level.WARNING);
      }
      else if ("error".equals(new_level)) {
	logger.setLevel(Level.SEVERE);
      }
      else {
	throw Context.reportRuntimeError("Level should be 'debug', 'info', 'warn' or 'error', "
					 + "not '" + new_level + "'");
      }

      lastLevel = new_level_obj;
    }

    LogRecord lr = new LogRecord(level, msg);
    lr.setSourceClassName(ident);
    lr.setSourceMethodName(null);

    logger.log(lr);
  }

  private Application app;
  private Request req;
  private Logger logger;
  private String ident;
  private Object lastLevel;
}
