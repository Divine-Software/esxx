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

package org.esxx.util;

import java.io.*;
import java.util.logging.*;

public class SyslogHandler
  extends Handler {
  public SyslogHandler(String ident) {
    try {
      syslog = new Syslog(ident, 0, Syslog.LOG_DAEMON);
    }
    catch (Syslog.SyslogException ex) {
      throw new UnsupportedOperationException(ex);
    }
  }

  @Override public synchronized void publish(LogRecord record) {
    if (!isLoggable(record)) {
      return;
    }

    String message = SYSLOG_FORMATTER.format(record);
    int priority   = record.getLevel().intValue();

    if (priority >= Level.SEVERE.intValue()) {
      priority = Syslog.LOG_ERR;
    }
    else if (priority >= Level.WARNING.intValue()) {
      priority = Syslog.LOG_WARNING;
    }
    else if (priority >= (Level.WARNING.intValue() + Level.INFO.intValue()) / 2) {
      priority = Syslog.LOG_NOTICE;
    }
    else if (priority >= Level.INFO.intValue()) {
      priority = Syslog.LOG_INFO;
    }
    else {
      priority = Syslog.LOG_DEBUG;
    }

    try {
      syslog.syslog(priority, message);
    }
    catch (Exception ex) {
      reportError(null, ex, ErrorManager.WRITE_FAILURE);
    }
  }

  @Override public boolean isLoggable(LogRecord record) {
    if (syslog == null) {
      return false;
    }
    
    return super.isLoggable(record);
  }

  @Override public void flush() {
    // Nothing to do
  }

  @Override public void close() {
    syslog = null;
  }

  private static class SyslogFormatter 
    extends Formatter {
    public synchronized String format(LogRecord record) {
      StringBuffer sb = new StringBuffer();

      if (record.getSourceClassName() != null) {      
	sb.append(record.getSourceClassName());
      } 
      else {
	sb.append(record.getLoggerName());
      }

      if (record.getSourceMethodName() != null) {     
	sb.append(": ");
	sb.append(record.getSourceMethodName());
      }

      sb.append(": ");
      sb.append(formatMessage(record));

      if (record.getThrown() != null) {
	try {
	  StringWriter sw = new StringWriter();
	  PrintWriter pw = new PrintWriter(sw);
	  record.getThrown().printStackTrace(pw);
	  pw.close();
	  sb.append(": ");
	  sb.append(sw.toString());
	} 
	catch (Exception ex) {
	  // Never mind
	}
      }

      return sb.toString();
    }
  }

  private static final Formatter SYSLOG_FORMATTER = new SyslogFormatter();
  private Syslog syslog;
  private boolean killSource;
}
