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

package org.esxx;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.esxx.request.AsyncServletRequest;


/** An HttpServlet that executes ESXX applications using the AsyncServletRequest. */
public class AsyncESXXServlet extends ESXXServlet {
  static final long serialVersionUID = 1565501106109643012L;

  @Override
  protected void service(HttpServletRequest sreq, HttpServletResponse sres)
    throws ServletException, IOException {
    AsyncServletRequest.handleServletRequest(sreq, sres, fsRootURI, "Servlet Error");
  }
}
