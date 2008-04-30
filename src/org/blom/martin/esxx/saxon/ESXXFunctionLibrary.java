/*
     ESXX - The friendly ECMAscript/XML Application Server
     Copyright (C) 2007 Martin Blom <martin@blom.org>

     This program is free software; you can redistribute it and/or
     modify it under the terms of the GNU General Public License
     as published by the Free Software Foundation; either version 2
     of the License, or (at your option) any later version.

     This program is distributed in the hope that it will be useful,
     but WITHOUT ANY WARRANTY; without even the implied warranty of
     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
     GNU General Public License for more details.

     You should have received a copy of the GNU General Public License
     along with this program; if not, write to the Free Software
     Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
*/

package org.blom.martin.esxx.saxon;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;

public class ESXXFunctionLibrary
  implements FunctionLibrary {

  public ESXXFunctionLibrary() {
  }

  public boolean isAvailable(StructuredQName function_name, 
			     int             arity) {
    System.err.println("Checking for " + function_name + " with " + arity + " arguments.");
    return function_name.getNamespaceURI().startsWith("javascript:");
  }

  public Expression bind(StructuredQName    function_name, 
			 final Expression[] static_args, 
			 StaticContext      env)
    throws XPathException {
    String uri = function_name.getNamespaceURI();

    if (! uri.startsWith("javascript:")) {
      return null;
    }

    String object = uri.substring("javascript:".length());
    String method = function_name.getLocalName();

    return new ESXXExpression(object, method, static_args);
  }

  public FunctionLibrary copy() {
    ESXXFunctionLibrary c = new ESXXFunctionLibrary();
    return c;
  }
}
