package org.htmlcleaner;

import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * <p>DOM serializer - creates xml DOM.</p>
 *
 * Created by: Vladimir Nikic<br/>
 * Date: April, 2007.
 */
public class NSDomSerializer {

    protected CleanerProperties props;
    protected boolean escapeXml = true;

    public NSDomSerializer(CleanerProperties props, boolean escapeXml) {
        this.props = props;
        this.escapeXml = escapeXml;
    }

    public NSDomSerializer(CleanerProperties props) {
        this(props, true);
    }

    public Document createDOM(TagNode rootNode) throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        Document document = factory.newDocumentBuilder().newDocument();
        Element rootElement = document.createElement(rootNode.getName());
        document.appendChild(rootElement);

        createSubnodes(document, rootElement, rootNode.getChildren());

        return document;
    }

    private void createSubnodes(Document document, Element element, List tagChildren) {
        if (tagChildren != null) {
	    for (Object item : tagChildren) {
                if (item instanceof CommentToken) {
                    CommentToken commentToken = (CommentToken) item;
                    Comment comment = document.createComment( commentToken.getContent() );
                    element.appendChild(comment);
                } else if (item instanceof ContentToken) {
                    String nodeName = element.getNodeName();
                    ContentToken contentToken = (ContentToken) item;
                    String content = contentToken.getContent();
                    boolean specialCase = props.isUseCdataForScriptAndStyle() &&
                                          ("script".equalsIgnoreCase(nodeName) || "style".equalsIgnoreCase(nodeName));

		    // Some characters simply cannot occur in an
		    // XML document, encoded or not. Remove them.
		    content = nonXMLChars.matcher((String) content).replaceAll("");

                    if (escapeXml && !specialCase) {
                        content = Utils.escapeXml(content, props, true);
                    }
                    element.appendChild( specialCase ? document.createCDATASection(content) : document.createTextNode(content) );
                } else if (item instanceof TagNode) {
                    TagNode subTagNode = (TagNode) item;
		    String elemName = subTagNode.getName();
		    String elemNS = getNS(element, elemName);
                    Element subelement = document.createElementNS(elemNS, elemName );
                    Map attributes =  subTagNode.getAttributes();
		    for (Object e : attributes.entrySet()) {
			Map.Entry entry = (Map.Entry) e;
                        String attrName = (String) entry.getKey();
			String attrNS = getNS(subelement, attrName);
                        String attrValue = (String) entry.getValue();
			
			// Some characters simply cannot occur in an
			// XML document, encoded or not. Remove them.
			attrValue = nonXMLChars.matcher(attrValue).replaceAll("");

                        if (escapeXml) {
                            attrValue = Utils.escapeXml(attrValue, props, true);
                        }

			if (!attrName.equals("xmlns") &&
			    !attrName.startsWith("xmlns:")) {
			  subelement.setAttributeNS(attrNS, attrName, attrValue);
			}
                    }

                    // recursively create subnodes
                    createSubnodes(document, subelement, subTagNode.getChildren());

                    element.appendChild(subelement);
                } else if (item instanceof List) {
                    List sublist = (List) item;
                    createSubnodes(document, element, sublist);
                }
            }
        }
    }

    private String getNS(Element element, String nodeName) {
        String elemNS = null;

	if (nodeName.indexOf(":") != -1) {
            String prefix = nodeName.substring(0, nodeName.indexOf(":"));
	    elemNS = element.lookupNamespaceURI(prefix);
      
	    if (elemNS == null) {
	        elemNS = prefix; // Ugh. Use prefix as URI.
	    }
	}

	return elemNS;
    }

    // XML 1.0 Characters looks kind of like this
    private java.util.regex.Pattern nonXMLChars = 
      java.util.regex.Pattern.compile("[^\t\n\r\\x20-\uD7FF\uE000-\uFFFD]");
}
