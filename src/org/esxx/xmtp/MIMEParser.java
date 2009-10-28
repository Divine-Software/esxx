/*
     ESXX - The friendly ECMAscript/XML Application Server
     Copyright (C) 2007-2008 Martin Blom <martin@blom.org>

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

package org.esxx.xmtp;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import javax.mail.*;
import javax.mail.internet.*;
import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.NSDomSerializer;
import org.htmlcleaner.TagNode;
import org.w3c.dom.*;
import org.w3c.dom.ls.*;
import org.w3c.dom.bootstrap.*;

public class MIMEParser {
    public MIMEParser(boolean xmtp, boolean use_ns,
		      boolean process_html, boolean strip_js, boolean add_preamble)
      throws MessagingException, IOException,
      ClassNotFoundException, InstantiationException, IllegalAccessException {

      if (strip_js && !process_html) {
	throw new IllegalArgumentException("If 'strip_js' is true, 'process_html' must be too");
      }

      // Make "mail.mime.decodeparameters" true if unspecified. Note
      // that this property is a System property and not a
      // Session.getInstance() parameter.
 
      Properties p = System.getProperties();
      p.setProperty("mail.mime.decodeparameters",
		    p.getProperty("mail.mime.decodeparameters", "true"));
 
      this.session = Session.getInstance(p);

      this.xmtpMode    = xmtp;
      this.procHTML    = process_html;
      this.stripJS     = strip_js;
      this.addPreamble = add_preamble;

      if (use_ns) {
	if (xmtpMode) {
	  documentNS     = XMTP_NAMESPACE;
	  documentPrefix = "";
	}
	else {
	  documentNS     = MIME_NAMESPACE;
	  documentPrefix = "m:";
	}
      }
      else {
	documentNS     = "";
	documentPrefix = "";
      }

      DOMImplementationRegistry reg  = DOMImplementationRegistry.newInstance();

      domImplementation   = reg.getDOMImplementation("XML 3.0");
      domImplementationLS = (DOMImplementationLS) domImplementation.getFeature("LS", "3.0");

      document = domImplementation.createDocument(documentNS, documentPrefix + "Message", null);
    }

    public String getNamespace() {
      return documentNS;
    }

    public Document getDocument() {
      return document;
    }

    public String getString() {
      LSSerializer ser = domImplementationLS.createLSSerializer();

      return ser.writeToString(document);
    }

    public void writeDocument(Writer wr) {
      LSSerializer ser = domImplementationLS.createLSSerializer();
      LSOutput     out = domImplementationLS.createLSOutput();

      out.setCharacterStream(wr);
      ser.write(document, out);
    }

    private static final int STRING_PART = 1;
    private static final int MULTI_PART  = 2;
    private static final int PLAIN_PART  = 3;
    private static final int XML_PART    = 4;
    private static final int HTML_PART   = 5;	// Only used if processing HTML
    private static final int RFC822_PART = 6;
    private static final int BASE64_PART = 7;


    public String convertMessage(InputStream is)
      throws IOException, MessagingException {
      MimeMessage msg = new MimeMessage(session, is);
      convertMessage(document.getDocumentElement(), msg);
      return msg.getMessageID();
    }

    private void convertMessage(Element element, MimeMessage msg)
      throws IOException, MessagingException {
      this.message = msg;
      convertPart(element, message, "mid:", message.getMessageID());
    }


    protected void convertPart(Element element, Part part, String about_prefix, String about)
      throws IOException, MessagingException {

      if (about == null && part instanceof MimePart) {
	about = ((MimePart) part).getContentID();
      }

      if (about != null && about.matches("^<.*>$")) {
	about = about.substring(1, about.length() - 1);
      }

      if (about != null) {
	if (xmtpMode) {
	  element.setAttributeNS(RDF_NAMESPACE, "web:about", about_prefix + about);
	}
	else {
	  element.setAttribute("id", about_prefix + about);
	}
      }

      ContentType content_type = null;

      try {
	content_type = new ContentType(part.getContentType());
      }
      catch (ParseException ex) {
	// Sigh, Content-Type is fucked up
	String ct = part.getContentType();

	// Php/libMail?
	// Content-Type: text/plain; charset=ISO-8859-15"
	if (ct.endsWith("\"")) {
	  try {
	    String unquoted = ct.substring(0, ct.length() - 1);
	    content_type = new ContentType(unquoted);
	    ct = unquoted;
	  }
	  catch (ParseException ignored) {}
	}

	if (content_type == null) {
	  // Try with just the first word or without params
	  ct = ct.replaceAll("[\\s;].*", "");

	  if (ct.matches("(text|plain)")) {
	    ct = "text/plain";
	  }

	  try {
	    content_type = new ContentType(ct);
	  }
	  catch (ParseException ex2) {
	    ct = "application/octet-stream"; // Give up
	    content_type = new ContentType(ct);
	  }
	}

	part.setHeader("Content-Type", ct);
      }

      int    part_type;
      Object content   = part.getContent();

      String base_type = content_type.getBaseType().toLowerCase();
      String charset   = content_type.getParameter("charset");
      InputStream content_stream = null;

      if (charset == null) {
	charset = "US-ASCII"; // or US-ASCII?
      }

      if (content instanceof String) {
	if (base_type.endsWith("/xml") || base_type.endsWith("+xml")) {
	  part_type = XML_PART;
	}
	else if (procHTML && base_type.equals("text/html")) {
	  part_type = HTML_PART;
	}
	else {
	  part_type = STRING_PART;
	}
      }
      else if (content instanceof MimeMessage) {
	part_type = RFC822_PART;
      }
      else if (content instanceof Multipart) {
	part_type = MULTI_PART;
      }
      else if (content instanceof Part) {
	part_type = PLAIN_PART;
      }
      else {
	content_stream = (InputStream) content;

	if (base_type.endsWith("/xml") || base_type.endsWith("+xml")) {
	  part_type = XML_PART;
	}
	else if (procHTML && base_type.equals("text/html")) {
	  part_type = HTML_PART;
	}
	else if (base_type.startsWith("text/")) {
	  part_type = STRING_PART;
	}
	else {
	  part_type = BASE64_PART;
	}
      }

      convertHeaders(element, part, part.getAllHeaders(),
		     part_type == BASE64_PART ? "base64" : null);

      Element body = document.createElementNS(documentNS, documentPrefix + "Body");
      element.appendChild(body);

      switch (part_type) {
        case STRING_PART: {
	  if (content_stream != null) {
	    InputStreamReader isr = new InputStreamReader(content_stream, charset);
	    StringWriter sw = new StringWriter();
	    char[] buf = new char[4096];
	    int len;

	    while ((len = isr.read(buf)) != -1) {
	      sw.write(buf, 0, len);
	    }

	    content = sw.toString();
	  }

	  // Nuke all obviously illegal characters
	  String value = nonXMLChars.matcher((String) content).replaceAll("");
	  convertTextPart(body, value);
	  break;
	}

	case MULTI_PART: {
	  Multipart mp = (Multipart) content;

	  // Add preample as a plain text node first in the Body if
	  // addPreamble is true
	  if (addPreamble && mp instanceof MimeMultipart) {
	    MimeMultipart mmp = (MimeMultipart) mp;

	    if (mmp.getPreamble() != null) {
	      body.appendChild(document.createTextNode(mmp.getPreamble()));
	    }
	  }

	  for (int i = 0; i < mp.getCount(); ++i) {
	    Element msg = document.createElementNS(documentNS, documentPrefix + "Message");
	    body.appendChild(msg);
	    convertPart(msg, mp.getBodyPart(i), "cid:", null);
	  }
	  break;
	}

	case PLAIN_PART: {
	  Element msg = document.createElementNS(documentNS, documentPrefix + "Message");
	  body.appendChild(msg);
	  convertPart(msg, (Part) content, "cid:", null);
	  break;
	}

	case HTML_PART: {
	  // We can only arrive here if we're processing HTML parts

	  HtmlCleaner       hc = new HtmlCleaner();
	  CleanerProperties hp = hc.getProperties();
	  TagNode           tn;

	  hp.setHyphenReplacementInComment("\u2012\u2012");
	  //	  hp.setOmitDoctypeDeclaration(false);
	  hp.setOmitDoctypeDeclaration(true); // Or we get an XML parser error :-(

	  if (content_stream != null) {
	    tn = hc.clean(content_stream, charset);
	  }
	  else {
	    tn = hc.clean((String) content);
	  }

	  // Update content type
	  replaceContentTypeElement(element, "text/x-html+xml");
	  
	  try {
	    Document doc = new NSDomSerializer(hp, true).createDOM(tn);
	    
	    if (stripJS) {
	      stripJS(doc.getDocumentElement());
	    }

	    convertDOMPart(body, doc);
	  }
	  catch (javax.xml.parsers.ParserConfigurationException ex) {
	    ex.printStackTrace();
	    throw new IOException("Failed to serialize HTML to XML: " + ex.getMessage(), ex);
	  }
	  break;
	}

	case XML_PART: {
	  try {
	    LSInput  input  = domImplementationLS.createLSInput();
	    LSParser parser = domImplementationLS.createLSParser(
	      DOMImplementationLS.MODE_SYNCHRONOUS, null);
	    parser.getDomConfig().setParameter("resource-resolver", new LSResourceResolver() {
		    public LSInput resolveResource(String type, String ns_uri, 
						   String public_id, String system_id, 
						   String base_uri) {
			// Never resolve anything external
			LSInput res = domImplementationLS.createLSInput();
			res.setStringData(" "); // Xerces checks for
						// NULL or empty
						// string ... duh.
			return res;
		    }
		});

	    if (content_stream != null) {
	      input.setByteStream(content_stream);
	    }
	    else {
	      input.setStringData((String) content);
	    }

	    Document doc = parser.parse(input);
	    convertDOMPart(body, doc);
	  }
	  catch (Exception ex) {
	    ex.printStackTrace();
	    throw new IOException("Failed to parse XML: " + ex.getMessage(), ex);
	  }
	  break;
	}

	case RFC822_PART: {
	  Element msg = document.createElementNS(documentNS, documentPrefix + "Message");
	  body.appendChild(msg);

	  // This is a bit ugly, but whatever
	  MimeMessage saved_message = message;
	  convertMessage(msg, (MimeMessage) content);
	  message = saved_message;
	  break;
	}

	case BASE64_PART: {
	  convertBase64Part(body, content_stream);
	  break;
	}
      }
    }

    private void convertHeaders(Element element,
				Part part,
				Enumeration<?> headers,
				String forced_encoding)
      throws MessagingException {
      while (headers.hasMoreElements()) {
	Header hdr  = (Header) headers.nextElement();
	String name = hdr.getName();

	try {
	  if (name.equalsIgnoreCase("Content-Type")) {
	    // Parse Content-Type
	    ContentType ct = new ContentType(hdr.getValue());

	    if (!xmtpMode) {
	      // Delete the boundary parameter, which is not interesting
	      ct.getParameterList().remove("boundary");
	    }

 	    decodeMIMEParams(ct.getParameterList());
	    convertResourceHeader(element, "Content-Type",
				  ct.getBaseType(),  ct.getParameterList());
	    continue;
	  }
	  else if (name.equalsIgnoreCase("Content-Disposition")) {
	    // Parse Content-Disposition
	    ContentDisposition cd = new ContentDisposition(hdr.getValue());

 	    decodeMIMEParams(cd.getParameterList());
	    convertResourceHeader(element, "Content-Disposition",
				  cd.getDisposition(),  cd.getParameterList());
	    continue;
	  }
	  else if (name.equalsIgnoreCase("From")) {
	    // Clean up often misspelled and misformatted header
	    convertAddressHeader(element, "From", message.getFrom());
	    continue;
	  }
	  else if (name.equalsIgnoreCase("Sender")) {
	    // Clean up often misspelled and misformatted header
	    convertAddressHeader(element, "Sender", new Address[] { message.getSender() });
	    continue;
	  }
	  else if (name.equalsIgnoreCase("Reply-To")) {
	    // Clean up often misspelled and misformatted header
	    convertAddressHeader(element, "Reply-To", message.getReplyTo());
	    continue;
	  }
	  else if (name.equalsIgnoreCase("To")) {
	    // Clean up often misspelled and misformatted header
	    convertAddressHeader(element, "To", message.getRecipients(Message.RecipientType.TO));
	    continue;
	  }
	  else if (name.equalsIgnoreCase("Cc")) {
	    // Clean up often misspelled and misformatted header
	    convertAddressHeader(element, "Cc", message.getRecipients(Message.RecipientType.CC));
	    continue;
	  }
	  else if (name.equalsIgnoreCase("Bcc")) {
	    // Clean up often misspelled and misformatted header
	    convertAddressHeader(element, "Bcc", message.getRecipients(Message.RecipientType.BCC));
	    continue;
	  }
	  else if (name.equalsIgnoreCase("Newsgroups")) {
	    // Clean up often misspelled and misformatted header
	    convertAddressHeader(element, "Newsgroups",
				 message.getRecipients(MimeMessage.RecipientType.NEWSGROUPS));
	    continue;
	  }
	  else if (name.equalsIgnoreCase("Date")) {
	    Date date = message.getSentDate();

	    if (date != null) {
	      if (xmtpMode) {
		convertPlainHeader(element, "Date", RFC2822_DATEFORMAT.format(date));
	      }
	      else {
		convertPlainHeader(element, "Date", ATOM_DATEFORMAT.format(date));
	      }
	    }
	    continue;
	  }
	}
	catch (ParseException pex) {
	  // Treat header as plain header then
	}

	String value = decodeMIMEValue(hdr.getValue());

	if (forced_encoding != null && name.equalsIgnoreCase("Content-Transfer-Encoding")) {
	  value = forced_encoding;
	}

	convertPlainHeader(element, name, value);
      }
    }


    protected void convertPlainHeader(Element element, String name, String value)
      throws MessagingException {
      Element e = document.createElementNS(documentNS, documentPrefix + makeXMLName(name));

      element.appendChild(e);

      if (value.length() != 0) {
	e.setTextContent(value);
      }
    }

    protected void convertResourceHeader(Element element, String name,
					 String value, ParameterList params)
      throws MessagingException {
      Element e = document.createElementNS(documentNS, documentPrefix + makeXMLName(name));

      element.appendChild(e);

      if (xmtpMode) {
	e.setAttributeNS(RDF_NAMESPACE, "web:parseType", "Resource");


	Element e2 = document.createElementNS(RDF_NAMESPACE, "web:value");
	e2.setTextContent(value.toLowerCase());

	e.appendChild(e2);

	for (Enumeration<?> names = params.getNames(); names.hasMoreElements(); ) {
	  String param = (String) names.nextElement();

	  e2 = document.createElementNS(XMTP_NAMESPACE, makeXMLName(param));
	  e2.setTextContent(params.get(param));

	  e.appendChild(e2);
	}
      }
      else {
	e.setTextContent(value.toLowerCase());

	for (Enumeration<?> names = params.getNames(); names.hasMoreElements(); ) {
	  String param = (String) names.nextElement();

	  e.setAttribute(makeXMLName(param), params.get(param));
	}
      }
    }

    protected void convertAddressHeader(Element element, String name, Address[] addresses)
      throws MessagingException {
      Element e = document.createElementNS(documentNS, documentPrefix + makeXMLName(name));

      StringBuilder sb = new StringBuilder();
      for (Address a : addresses) {
	if (sb.length() != 0) sb.append(",\r\n    ");

	if (a instanceof InternetAddress) {
	  sb.append(((InternetAddress) a).toUnicodeString());
	}
	else {
	  sb.append(a.toString());
	}
      }

      // Nuke all obviously illegal characters
      String value = nonXMLChars.matcher(sb.toString()).replaceAll("");
      e.setTextContent(value);
      element.appendChild(e);
    }

    protected void convertTextPart(Element element, String content)
      throws IOException, MessagingException {
      element.setTextContent(content);
    }

    protected void convertDOMPart(Element element, Document doc)
      throws IOException, MessagingException {
      // Add some extra info
      DocumentType doctype = doc.getDoctype();

      if (xmtpMode) {
	element.setAttributeNS(RDF_NAMESPACE, "web:parseType", "Literal");
      }
      else {
	element.setAttribute("version", doc.getXmlVersion());
	element.setAttribute("encoding", doc.getXmlEncoding());
	element.setAttribute("standalone", doc.getXmlStandalone() ? "yes" : "no");

	if (doctype != null) {
	  element.setAttribute("doctype-public", doctype.getPublicId());
	  element.setAttribute("doctype-system", doctype.getSystemId());
	}
      }


      Node adopted = document.adoptNode(doc.getDocumentElement());

      if (adopted == null) {
	adopted = document.importNode(doc.getDocumentElement(), true);
      }
      element.appendChild(adopted);
    }

    protected void convertBase64Part(Element element, InputStream is)
      throws IOException, MessagingException {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      Base64.OutputStream b64os = new Base64.OutputStream(bos, Base64.ENCODE);
      byte[] buffer = new byte[4096];
      int bytes_read;

      while ((bytes_read = is.read(buffer)) != -1) {
	b64os.write(buffer, 0, bytes_read);
      }

      b64os.close();
      element.setTextContent(bos.toString("US-ASCII"));
    }

    private String makeXMLName(String s) {
      char[] chars = s.toCharArray();

      if(!isNameStartChar(chars[0])) {
	chars[0] = '_';
      }

      for (int i = 1; i < chars.length; ++i) {
	if (!isNameChar(chars[i])) {
	  chars[i] = '_';
	}
      }

      return new String(chars);
    }

    // private java.util.regex.Pattern controlChars = 
    // 	java.util.regex.Pattern.compile("\\p{Cntrl}");

    // XML 1.0 Characters looks kind of like this
    private java.util.regex.Pattern nonXMLChars = 
      java.util.regex.Pattern.compile("[^\t\n\r\\x20-\uD7FF\uE000-\uFFFD]");

    private String decodeMIMEValue(String value) {
      if (value != null) {
	try {
	  value = MimeUtility.decodeText(value);
	}
	catch (Exception ex) {
	  // Use raw value
	}
      }

      // Nuke all obviously illegal characters
      value = nonXMLChars.matcher(value).replaceAll("");

      return value;
    }

    private void decodeMIMEParams(ParameterList params) {
      // Strictly speaking, params may not be encoded, but the real
      // world is not strict ...
      for (Enumeration<?> e = params.getNames(); e.hasMoreElements(); ) {
	String name = (String) e.nextElement();
	
	params.set(name, decodeMIMEValue(params.get(name)));
      }
    }

    private void replaceContentTypeElement(Element parent, String content_type) {
      Element ct = (Element) parent.getElementsByTagNameNS("*", "Content-Type").item(0);
      if (ct != null) {
	ct.setTextContent(content_type);

	NamedNodeMap nodes = ct.getAttributes();

	while (nodes.getLength() > 0) {
	  nodes.removeNamedItem(nodes.item(0).getNodeName());
	}
      }
    }

    private Pattern forbiddenURL 
      = Pattern.compile("(about|javascript|livescript|mocha|vbscript):");
    

    private List<Node> nodeReferences(NodeList nl) {
      // Convert NodeList into something that doesn't change when
      // nodes are removed from the document
      ArrayList<Node> list = new ArrayList<Node>(nl.getLength());

      for (int i = 0; i < nl.getLength(); ++i) {
    	list.add(nl.item(i));
      }
      
      return list;
    }

    private List<Node> nodeReferences(NamedNodeMap nl) {
      // Convert NodeList into something that doesn't change when
      // nodes are removed from the document
      ArrayList<Node> list = new ArrayList<Node>(nl.getLength());

      for (int i = 0; i < nl.getLength(); ++i) {
    	list.add(nl.item(i));
      }

      return list;
    }

    private String nodeName(Node n) {
      String name = n.getLocalName();

      if (name == null) {
	name = n.getNodeName();
      }

      return name;
    }

    private void stripJS(Element elem) {
      String name = nodeName(elem);

      if ("script".equalsIgnoreCase(name)) {
	// All script tags goes -- obviously
	elem.getParentNode().removeChild(elem);
	return;
      }
      else if ("style".equalsIgnoreCase(name)) {
	Matcher m = forbiddenURL.matcher(elem.getTextContent());
	if (m.find()) {
	  // If a inline-CSS contains a suspicious URL, nuke them!
	  elem.setTextContent(m.replaceAll("X-NUKED:"));
	}
      }

      attr: for (Node n : nodeReferences(elem.getAttributes())) {
	Attr attr = (Attr) n;

	name = nodeName(attr).toLowerCase();

	String value = attr.getValue().trim().toLowerCase();

	if (name.startsWith("on")) {
	  // All attributes that begins with 'on' are assumed to be
	  // script handlers and will be removed
	  elem.removeAttributeNode(attr);
	  continue attr;
	}
	else if (name.equals("type") &&
		 (value.indexOf("javascript") != -1 ||
		  value.indexOf("vbscript") != -1)) {
	  // Elements with a type attribute that contains the word
	  // 'javascript' will also be removed (handles <style
	  // type='text/javascript>{code}</style>)
	  elem.getParentNode().removeChild(elem);
	  break attr;
	}

	Matcher m = forbiddenURL.matcher(value);

	if (m.find()) {
	  attr.setValue(m.replaceAll("X-NUKED:"));
	}
      }

      // Process all children
      for (Node n : nodeReferences(elem.getChildNodes())) {
	if (n.getNodeType() == Node.ELEMENT_NODE) {
	  stripJS((Element) n);
	}
      }
    }

    private static boolean isNameStartChar(char ch) {
      return (Character.isLetter(ch) || ch == '_');
    }

    private static boolean isNameChar(char ch) {
      return (isNameStartChar(ch) || Character.isDigit(ch) || ch == '.' || ch == '-');
    }


    static final String RDF_NAMESPACE  = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    static final String XMTP_NAMESPACE = "http://www.openhealth.org/xmtp#";
    static final String MIME_NAMESPACE = "urn:x-i-o-s:xmime";

    static javax.mail.internet.MailDateFormat RFC2822_DATEFORMAT =
      new javax.mail.internet.MailDateFormat();

    static java.text.SimpleDateFormat ATOM_DATEFORMAT =
      new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);

    private Session session;
    private MimeMessage message;
    private boolean xmtpMode;
    private boolean procHTML;
    private boolean stripJS;
    private boolean addPreamble;

    protected String documentNS;
    protected String documentPrefix;

    private Document document;

    private DOMImplementation domImplementation;
    private DOMImplementationLS domImplementationLS;
}
