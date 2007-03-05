
package org.mozilla.javascript.xmlimpl;

import org.mozilla.javascript.xmlimpl.XML;
import org.mozilla.javascript.xml.XMLObject;
import org.apache.xmlbeans.XmlObject;

public class UglyHack {
    public static XmlObject getXmlObject(XMLObject xml) {
      return ((XML) xml).getXmlObject();
    }
}