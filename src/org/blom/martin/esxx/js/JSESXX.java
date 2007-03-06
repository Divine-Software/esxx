
package org.blom.martin.esxx.js;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Properties;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.blom.martin.esxx.Workload;
import org.mozilla.javascript.*;

public class JSESXX {
    public JSESXX(Context cx, Scriptable scope, Workload workload,
		  XmlObject document, URL stylesheet) {
      this.debug      = new PrintWriter(workload.getDebugWriter());
      this.error      = new PrintWriter(workload.getErrorWriter());
      this.properties = cx.newObject(scope, "Object");
      for (String name :  workload.getProperties().stringPropertyNames()) {
	ScriptableObject.putProperty(this.properties, name, 
				     workload.getProperties().getProperty(name));
      }

//      this.properties = workload.getProperties();

      this.document = cx.newObject(scope, "XML", new Object[] { cx.javaToJS(document, scope) });

//       this.result = cx.newObject(scope, "XML", new Object[] { 
// 				   cx.javaToJS(XmlObject.Factory.newInstance(), 
// 					       scope)
// 				 });

      this.headers = cx.newObject(scope, "Object");
      ScriptableObject.putProperty(this.headers, "Status", "200 OK");
      ScriptableObject.putProperty(this.headers, "Cookies", cx.newObject(scope, "Object"));

      this.stylesheet = (stylesheet != null ? stylesheet.toString() : "");
    }

    public PrintWriter error;
    public PrintWriter debug;

    public Scriptable properties;
    public Scriptable headers;
    public Scriptable document;
//     public Scriptable result;

    public String stylesheet;
}
