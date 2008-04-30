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

import net.sf.saxon.expr.*;
import net.sf.saxon.functions.*;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.*;

public class ESXXExpression 
  extends SimpleExpression {
  private String object;
  private String method;

  public ESXXExpression(String object, String method, Expression[] args) {
    super();

    System.err.println("ESXXExpression: " + object + " -> " + method 
		       + " ("  + args.length + " args)");
    this.object = object;
    this.method = method;

    setArguments(args);
  }

  public int getImplementationMethod() {
    return ITERATE_METHOD;
  }

  public SequenceIterator iterate(XPathContext context) 
    throws XPathException {

    Object[] args = new Object[arguments.length];

    System.err.println("Evaluating " + object + " -> " + method);

    for (int i = 0; i < arguments.length; ++i) {
      int mode = ExpressionTool.lazyEvaluationMode(arguments[i]);
	
      ValueRepresentation v = ExpressionTool.evaluate(arguments[i], mode, context, 1);

      System.err.println("ORIG " + arguments[i].getClass());
      System.err.println("VALUE " + v.getClass());
      args[i] = Value.convertToJava(Value.asItem(v));

      System.err.println("PARAM " + i + ": " + args[i].getClass());
    }

    Object result = (Object) new Object[] { "hej", 10, 2.3, false };
    Value  value  = Value.convertJavaObjectToXPath(result, SequenceType.ANY_SEQUENCE, context);

    return value.iterate();
  }
}
