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

package org.blom.martin.esxx.js;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import org.blom.martin.esxx.ESXX;
import org.mozilla.javascript.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class FileURI 
  extends UrlURI {
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

    protected Object delete(Context cx, Scriptable thisObj)
      throws Exception {

      File f = new File(uri);

      return new Boolean(f.delete());
    }
}
