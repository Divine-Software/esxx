
package org.blom.martin.esxx;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import org.mozilla.javascript.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class FileURI 
  extends JSURI {
    public FileURI(ESXX esxx, URI uri) {
      super(esxx, uri);
    }

    protected Object load(Context cx, Scriptable thisObj, 
			String type, HashMap<String,String> params)
      throws Exception {

      // Default file: load() media type is XML
      if (type == null) {
	type = "text/xml";
      }

      if (type.equals("text/xml")) {

	File dir = new File(uri);
	
	if (dir.exists() && dir.isDirectory()) {
	  Document result = createDirectoryListing(dir);

	  return esxx.domToE4X(result, cx, this);
	}
      }

      return super.load(cx, thisObj, type, params);
    }


    protected Document createDirectoryListing(File dir) {
      File[] list = dir.listFiles();

      Document result = esxx.createDocument("result");
      Element  root   = result.getDocumentElement();

      for (File f : list) {
	Element element = null;

	if (f.isDirectory()) {
	  element = result.createElement("directory");
	}
	else if (f.isFile()) {
	  element = result.createElement("file");
	  addChild(element, "length", Long.toString(f.length()));
	}
	      
	addChild(element, "name", f.getName());
	addChild(element, "path", f.getPath());
	addChild(element, "uri", f.toURI().toString());
	addChild(element, "hidden", f.isHidden() ? "true" : "false");
	addChild(element, "lastModified", Long.toString(f.lastModified()));
	addChild(element, "id", Integer.toHexString(f.hashCode()));
	root.appendChild(element);
      }

      return result;
    }
}
