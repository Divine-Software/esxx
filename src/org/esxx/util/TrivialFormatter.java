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

public class TrivialFormatter 
  extends Formatter {
  public TrivialFormatter(boolean include_level) {
    super();
    includeLevel = include_level;
  }

  @Override
  public synchronized String format(LogRecord record) {
    StringBuffer sb = new StringBuffer();

    if (includeLevel) {
      sb.append("[").append(record.getLevel()).append("] ");
    }

    if (record.getSourceClassName() != null) {      
      sb.append(record.getSourceClassName());
    } 
    else {
      sb.append(record.getLoggerName());
    }

    if (record.getSourceMethodName() != null) {     
      sb.append("::");
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

    sb.append('\n');
    return sb.toString();
  }

  private boolean includeLevel;
}
