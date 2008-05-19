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

package org.esxx.js;

import org.esxx.Application;
import java.util.logging.*;
import org.mozilla.javascript.*;

public class JSLogger
  extends ScriptableObject {
  public JSLogger() {
    super();
  }

  public JSLogger(Application app, Logger logger, String ident) {
    super();

    this.app    = app;
    this.logger = logger;
    this.ident  = ident;
  }

  static public Object jsConstructor(Context cx,
				     java.lang.Object[] args,
				     Function ctorObj,
				     boolean inNewExpr) {
    Application app = null;
    Logger   logger = null;
    String    ident = null;

    if (args.length >= 1 && args[0] instanceof Application) {
      app = (Application) args[0];
    }
    else if (args.length >= 1 && args[0] instanceof Logger) {
      logger = (Logger) args[0];
    }
    else if (args.length >= 1 && args[0] instanceof JSLogger) {
      app    = ((JSLogger) args[0]).app;
      logger = ((JSLogger) args[0]).logger;
      ident  = ((JSLogger) args[0]).ident;
    }
    else {
      logger = Logger.getLogger(JSLogger.class.getName());
    }

    if (args.length >= 2 && args[1] != Context.getUndefinedValue()) {
      ident = Context.toString(args[1]);
    }

    return new JSLogger(app, logger, ident);
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
    LogRecord lr = new LogRecord(level, msg);
    lr.setSourceClassName(ident);
    lr.setSourceMethodName(null);

    if (logger == null) {
      logger = app.getLogger();
    }

    logger.log(lr);
  }

  private Application app;
  private Logger logger;
  private String ident;
}
