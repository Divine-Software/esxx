/*
     ESXX - The friendly ECMAscript/XML Application Server
     Copyright (C) 2007-2008 Martin Blom <martin@blom.org>

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

package org.esxx.saxon;

import net.sf.saxon.dom.NodeOverNodeInfo;
import net.sf.saxon.dom.ElementOverNodeInfo;
import net.sf.saxon.dom.DocumentWrapper;
import net.sf.saxon.dom.DOMWriter;
import net.sf.saxon.expr.*;
import net.sf.saxon.functions.*;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.*;
import org.esxx.ESXX;
import org.esxx.ESXXException;
import org.mozilla.javascript.*;

import org.w3c.dom.Document;
import org.w3c.dom.UserDataHandler;
import org.w3c.dom.Node;
import java.util.Collection;

public class ESXXExpression 
  extends SimpleExpression {
  private String object;
  private String method;

  public ESXXExpression(String object, String method, Expression[] args) {
    super();

    this.object = object;
    this.method = method;

    setArguments(args);
  }

  public int getImplementationMethod() {
    return ITERATE_METHOD;
  }

  public SequenceIterator iterate(XPathContext context) 
    throws XPathException {
    Context    cx    = Context.getCurrentContext();
    Scriptable scope = (Scriptable) cx.getThreadLocal(ESXXExpression.class);
    Object[]   args  = new Object[arguments.length];

    for (int i = 0; i < arguments.length; ++i) {
      int mode = ExpressionTool.lazyEvaluationMode(arguments[i]);
      ValueRepresentation v = ExpressionTool.evaluate(arguments[i], mode, context, 1);
      args[i] = contvertToJS(v, context, cx, scope);
    }

    Object result = ESXX.callJSMethod(object, method, args, "XSLT Stylesheet", cx, scope);

    if (result instanceof NativeArray) {
      result = (Object) cx.getElements((NativeArray) result);
    }

    if (result instanceof org.mozilla.javascript.xml.XMLObject) {
      result = ESXX.e4xToDOM((Scriptable) result);
    }

    Value value = Value.convertJavaObjectToXPath(result, SequenceType.ANY_SEQUENCE, context);
    return value.iterate();
  }
  
  private static Object contvertToJS(Object value, XPathContext context, 
				     Context cx, Scriptable scope) 
    throws XPathException {
    if (value instanceof Value) {
      value = ((Value) value).convertToJava(Object.class, context);
    }

    if (value instanceof NodeInfo) {
      NodeInfo ni = (NodeInfo) value;

      if (ni.getNodeKind() == Type.ELEMENT) {
	value = ESXX.domToE4X(new ElementWrapper(ni), cx, scope);
      }
      else {
	value = ni.getStringValue();
      }
    }

    if (value instanceof Collection) {
      value = ((Collection) value).toArray();
    }

    if (value instanceof Object[]) {
      Object[] array = (Object[]) value;

      for (int i = 0; i < array.length; ++i) {
	array[i] = contvertToJS(array[i], context, cx, scope);
      }

      value = cx.newArray(scope, array);
    }

    return value;
  }


  private static class ElementWrapper
    extends ElementOverNodeInfo {
    private String key;
    private Object data;
    private UserDataHandler handler;

    ElementWrapper(NodeInfo ni) {
      super();
      node = ni;
    }

    // Rhino depends on this method
    public Object setUserData(String key, Object data, UserDataHandler handler) {
      Object old = data;

      this.key = key;
      this.data = data;
      this.handler = handler;

      return data;
    }

    // Rhino depends on this method
    public Object getUserData() {
      return data;
    }

    // Rhino depends on this method
    public Node cloneNode(boolean deep) {
      if (!deep) {
	throw new UnsupportedOperationException();
      }

      try {
	// Convert the node to a "real" DOM node
	ESXX esxx = ESXX.getInstance();
	Document doc = esxx.createDocument("ugly");
	DOMWriter dom_writer = new DOMWriter();
	dom_writer.setPipelineConfiguration(esxx.
					    getSaxonProcessor().
					    getUnderlyingConfiguration().
					    makePipelineConfiguration());
	dom_writer.setNode(doc.getDocumentElement());
	node.copy(dom_writer, NodeInfo.ALL_NAMESPACES, false, 0);

	// The returned node is not supposed to be part of a document
	Node ugly   = doc.getDocumentElement();
	Node result = ugly.removeChild(ugly.getFirstChild());

	// Call installed handler
	if (handler != null) {
	  handler.handle(UserDataHandler.NODE_CLONED, key, data, this, result);
	}

	return result;
      }
      catch (XPathException ex) {
	throw new ESXXException("ElementWrapper.cloneNode() failed: " + ex.getMessage(), ex);
      }
    }
  }
}
