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

package org.esxx.shell;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jline.Completor;
import org.esxx.util.JS;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.debug.DebuggableObject;

public class PropertyCompletor
  implements Completor {
  public PropertyCompletor(Scriptable scope) {
    this.scope = scope;
  }

  @SuppressWarnings(value = "unchecked")
  public int complete(String buffer, int cursor, List candidates) {
    int begin = cursor;
    int trail = -1;

    // Cut anything after the cursor
    buffer = buffer.substring(0, cursor);

    while (begin > 0) {
      char c = buffer.charAt(begin - 1);

      if (c == '.') {
	if (trail == -1) {
	  trail = begin - 1;
	}
      }
      else if (!Character.isJavaIdentifierPart(c)) {
	break;
      }

      --begin;
    }

    String prefix  = null;
    String postfix = null;

    if (trail != -1) {
      prefix  = buffer.substring(begin, trail);
      postfix = buffer.substring(trail + 1);
      cursor  = trail + 1;
    }
    else {
      postfix = buffer.substring(begin);
      cursor  = begin;
    }

    Scriptable base = JS.evaluateObjectExpr(prefix, scope);

    if (base != null) {
      Set<Object> members = getAllMembers(base);

      if (base == scope) {
	// Add JS keywords in global scope
	members.addAll(reserved);
      }

      for (Object o : members) {
	String name = Context.toString(o);
	if (name.startsWith(postfix)) {
	  candidates.add(name);
	}
      }

      if (candidates.size() == 1 && postfix.equals(candidates.get(0))) {
	candidates.clear();

	Object member = ScriptableObject.getProperty(base, postfix);

	if (member == Scriptable.NOT_FOUND ||
	    !(member instanceof Scriptable) ||
	    getAllMembers((Scriptable) member).isEmpty()) {
	  candidates.add(postfix + " ");
	}
	else if (member instanceof Scriptable) {
	  candidates.add(postfix + ".");
	}
      }
    }

    return cursor;
  }

  private static Set<Object> getAllMembers(Scriptable scope) {
    Set<Object> members = new HashSet<Object>();

    while (scope != null) {
      if (scope instanceof DebuggableObject) {
	members.addAll(Arrays.asList(((DebuggableObject) scope).getAllIds()));
      }
      else {
	members.addAll(Arrays.asList(scope.getIds()));
      }

      scope = scope.getPrototype();
    }

    return members;
  }

  private Scriptable scope;

  private static List<String> reserved = Arrays.asList(new String[]{
      // JS reserved
      "break", "case", "catch", "continue", "default", "delete", "do", 
      "else", "finally", "for", "function", "if", "in", "instanceof", 
      "new", "return", "switch", "this", "throw", "try", "typeof", 
      "var", "void", "while", "with",

      // Special
      "false", "true", "null", "undefined",

      // Mozilla
      "const"
    });
}
