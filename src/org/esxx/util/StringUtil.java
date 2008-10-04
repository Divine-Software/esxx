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

import java.util.regex.Pattern;
import java.util.regex.Matcher;

public abstract class StringUtil {
  public interface ParamResolver {
    public String resolveParam(String param);
  }

  public static String format(String format, ParamResolver resolver) {
    StringBuffer s = new StringBuffer();
    Matcher      m = paramPattern.matcher(format);

    while (m.find()) {
      String g = m.group();

      if (m.start(1) != -1) {
	// Match on group 1, which is our parameter pattern; append a single '?'
	m.appendReplacement(s, resolver.resolveParam(g.substring(1, g.length() - 1)));
      }
      else {
	// Match on quoted strings, which we just copy as-is
	m.appendReplacement(s, g);
      }
    }

    m.appendTail(s);

    return s.toString();
  }

  // TODO: Consider replacing all this with just \{ escape notation instead
  private static final String quotePattern1 = "('((\\\\')|[^'])+')";
  private static final String quotePattern2 = "(`((\\\\`)|[^`])+`)";
  private static final String quotePattern3 = "(\"((\\\\\")|[^\"])+\")";

  private static final Pattern paramPattern = Pattern.compile(
	"(\\{[^\\}]+\\})" +    // Group 1: Matches {identifier}
	"|" + quotePattern1 + "|" + quotePattern2 + "|" + quotePattern3);
}
