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

package org.esxx.js.protocol;

import org.esxx.util.JS;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

public class Auth {
    public Auth(Scriptable auth) {
      if (auth == null) {
	return;
      }

      Object u = auth.get("username", auth);
      Object p = auth.get("password", auth);

      Context cx = Context.getCurrentContext();

      try {
	Object[] us = cx.getElements((Scriptable) u);
	username  = JS.toStringOrNull(us[0]);
	username2 = us.length > 1 ? JS.toStringOrNull(us[1]) : null;
      }
      catch (Exception ignored) {
	username  = JS.toStringOrNull(u);
	username2 = null;
      }

      try {
	Object[] ps = cx.getElements((Scriptable) p);
	password  = JS.toStringOrNull(ps[0]);
	password2 = ps.length > 1 ? JS.toStringOrNull(ps[1]) : null;
      }
      catch (Exception ignored) {
	password  = JS.toStringOrNull(p);
	password2 = null;
      }

      mechanism  = JS.toStringOrNull(auth, "mechanism");
      realm      = JS.toStringOrNull(auth, "realm");
      preemptive = JS.toBoolean(auth, "preemptive");
    }

    public String getUsername() {
      return username;
    }

    public String getUsername2() {
      return username2;
    }

    public String getPassword() {
      return password;
    }

    public String getPassword2() {
      return password2;
    }

    public String getMechanism() {
      return mechanism;
    }

    public String getRealm() {
      return realm;
    }

    public boolean isPreemptive() {
      return preemptive;
    }

    private String username;
    private String username2;
    private String password;
    private String password2;
    private String mechanism;
    private String realm;
    private boolean preemptive;
}
