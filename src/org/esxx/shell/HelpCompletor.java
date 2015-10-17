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

package org.esxx.shell;

// import java.util.Arrays;
// import java.util.HashSet;
import java.util.List;
// import java.util.Set;
import jline.Completor;
import org.esxx.util.QueryCache;
import org.esxx.util.ArrayQueryHandler;
// import org.esxx.util.JS;
// import org.mozilla.javascript.Context;
// import org.mozilla.javascript.Scriptable;
// import org.mozilla.javascript.ScriptableObject;
// import org.mozilla.javascript.debug.DebuggableObject;

public class HelpCompletor
  implements Completor {

  public HelpCompletor(Shell shell) {
    this.shell = shell;
  }

  @SuppressWarnings(value = "unchecked")
    public int complete(String buffer, int cursor, List candidates) {
    int end;
    int begin;

    for (end = cursor, begin = cursor; begin > 0; --begin) {
      if (Character.isWhitespace(buffer.charAt(begin - 1))) {
	break;
      }
    }

    try {
      QueryCache help = shell.getHelpQuery();

      ArrayQueryHandler qh = new ArrayQueryHandler(new String[] { buffer.substring(begin, end)
								  + "%" });

      help.executeQuery(shell.getHelpURI(), null,
			"select word from words where word like {0} order by word",
			qh);

      List<String> words = qh.<String>getColumn(0);
      candidates.addAll(words);
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }

    return begin;
  }

  private Shell shell;
}
