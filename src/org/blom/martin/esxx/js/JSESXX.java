
package org.blom.martin.esxx.js;

import org.blom.martin.esxx.ESXX;

import java.io.PrintWriter;
import java.io.Reader;
import java.net.URL;
import java.util.Properties;
import org.blom.martin.esxx.Workload;
import org.mozilla.javascript.*;

import org.w3c.dom.Document;

public class JSESXX {
    public JSESXX(ESXX esxx, Context cx, Scriptable scope, Workload workload,
		  Document document, URL stylesheet) {

      this.in         = workload.getInReader();
      this.debug      = new PrintWriter(workload.getDebugWriter());
      this.error      = new PrintWriter(workload.getErrorWriter());

      this.properties = cx.newObject(scope, "Object");
      for (String name :  workload.getProperties().stringPropertyNames()) {
	ScriptableObject.putProperty(this.properties, name, 
				     workload.getProperties().getProperty(name));
      }

//      this.document = cx.newObject(scope, "XML", new Object[] { cx.javaToJS(document, scope) });

      String cmd = "<>" + esxx.serializeNode(document, true) + "</>;";
      this.document = (Scriptable) cx.evaluateString(scope, cmd, "<esxx.document>", 0, null);
// 	String cmd = ("s=XML.settings();" +
// 		      "XML.ignoreComments=false;" +
// 		      "XML.ignoreProcessingInstructions=false;" +
// 		      "esxx.document = <>" + ESXX.serializeNode(document, true) + "</>;" +
// 		      "XML.setSettings(s);" +
// 		      "delete s;");
// 	cx.evaluateString(scope, cmd, "<esxx.document>", 0, null);

      this.headers = cx.newObject(scope, "Object");
      ScriptableObject.putProperty(this.headers, "Status", "200 OK");
      ScriptableObject.putProperty(this.headers, "Cookies", cx.newObject(scope, "Object"));

      this.stylesheet = (stylesheet != null ? stylesheet.toString() : "");
    }

    public Reader      in;
    public PrintWriter error;
    public PrintWriter debug;

    public Scriptable properties;
    public Scriptable headers;
    public Scriptable document;

    public String stylesheet;
}
