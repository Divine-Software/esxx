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

import java.util.logging.*;

public class SyslogHandler
  extends Handler {

    /** A static utility method that returns a Logger for the given
     *  name. If no handlers are configured for this logger, it is set
     *  up with a SyslogHandler and a ConsoleHandler, both using the
     *  TrivialFormatter (however, the console handler will include
     *  the log level on each line, while the syslog handler will
     *  not).
     */

    public static synchronized Logger createLogger(String logger_name,
						   Level logger_level,
						   String syslog_ident) {
      Logger logger = Logger.getLogger(logger_name);

      if (logger.getHandlers().length == 0) {
	try {
	  // No specific log handler configured in
	  // jre/lib/logging.properties -- log everything to both
	  // syslog and console using the TrivialFormatter.

	  ConsoleHandler ch = new ConsoleHandler();

	  ch.setLevel(Level.ALL);
	  ch.setFormatter(getTrivialFormatter(true));

	  logger.addHandler(new SyslogHandler(syslog_ident));
	  logger.addHandler(ch);

	  logger.setUseParentHandlers(false);
	  logger.setLevel(logger_level);
	}
	catch (Throwable ex) {
	  // Probably a Google App Engine problem
	}
      }

      return logger;
    }

    private static synchronized Formatter getTrivialFormatter(boolean include_level) {
      int idx = include_level ? 0 : 1;

      if (trivialFormatters[idx] == null) {
	trivialFormatters[idx] = new TrivialFormatter(include_level);
      }

      return trivialFormatters[idx];
    }

    private static Formatter[] trivialFormatters = new Formatter[2];


    public SyslogHandler() {
      this(Thread.currentThread().getName());
    }

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

      Formatter formatter = getFormatter();

      if (formatter == null) {
	formatter = getTrivialFormatter(false);
	setFormatter(formatter);
      }

      String message;

      try {
	message = formatter.format(record);
      }
      catch (Exception ex) {
	reportError(null, ex, ErrorManager.FORMAT_FAILURE);
	return;
      }

      // Convert Java priority level to Syslog level
      int priority = record.getLevel().intValue();

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
	if (syslog != null) {
	  syslog.syslog(priority, message);
	}
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

    private Syslog syslog;
}
