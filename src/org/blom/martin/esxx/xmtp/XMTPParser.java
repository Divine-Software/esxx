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

package org.blom.martin.esxx.xmtp;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import javax.mail.*;
import javax.mail.internet.*;
import javax.xml.stream.*;

import static javax.xml.stream.XMLStreamConstants.*;

public class XMTPParser {
    public XMTPParser() {
      this.session = Session.getDefaultInstance(System.getProperties());
    }

    public String convertMessage(InputStream is) 
      throws XMLStreamException {
      return convertMessage(XMLInputFactory.newInstance().createXMLStreamReader(is));
    }

    public String convertMessage(Reader rd)
      throws XMLStreamException {
      return convertMessage(XMLInputFactory.newInstance().createXMLStreamReader(rd));
    }

    public MimeMessage getMessage() {
      return message;
    }

    private String convertMessage(XMLStreamReader xr)
      throws XMLStreamException {

      try {
	boolean exit = false;

	// We expect to see a <Message> element and nothing else

	while (!exit && xr.hasNext()) {
	  int ev = xr.nextTag();

	  switch (ev) {
	    case START_DOCUMENT:
	      break;

	    case START_ELEMENT: {
	      verifyNamespaceURI(xr);

	      if (xr.getLocalName().equals("Message")) {
		// Accepted
		message = new MimeMessage(session);
	      
		convertPart(xr, message);
		exit = true;
	      }
	      else {
		throw new XMLStreamException("Unsupported MIME+XML format");
	      }
	      break;
	    }

	    default:
	      throw new XMLStreamException("MIME+XML parser is messed up");
	  }
	}

	xr.close();

	return message != null ? message.getMessageID() : null;
      }
      catch (MessagingException ex) {
	throw new XMLStreamException("Unsupported MIME+XML format: " + ex.getMessage(),
				     ex);
      }
      catch (ClassCastException ex) {
	throw new XMLStreamException("Unsupported MIME+XML format", ex);
      }
    }


    protected void convertPart(XMLStreamReader xr, Part part)
      throws XMLStreamException, MessagingException {
      boolean exit = false;

      // We're inside a Message element, expecting only headers or a
      // <Body> element.

      while (!exit && xr.hasNext()) {
	int ev = xr.nextTag();

	switch (ev) {
	  case START_ELEMENT: {
	    verifyNamespaceURI(xr);

	    String name = xr.getLocalName();

	    System.err.println("Part element " + name);

	    if (name.equals("Body")) {
	      ContentType content_type = new ContentType(part.getContentType());

	      String base_type = content_type.getBaseType().toLowerCase();
	      String prim_type = content_type.getPrimaryType().toLowerCase();

	      System.err.println("*** " + part.getContentType());
	      
	      if (prim_type.equals("multipart")) {
		part.setContent(convertMultiPart(xr, content_type), 
				part.getContentType());
	      }
	      else if (base_type.endsWith("/xml") || base_type.endsWith("+xml")) {
//		part.setContent("<xml/>", part.getContentType());
		part.setContent("<xml/>", "text/plain");
		ignoreElement(xr);
	      }
	      else if (base_type.startsWith("text/")) {
		part.setContent(convertTextPart(xr), 
				part.getContentType());
	      }
	      else {
		part.setContent("<xml/>", "text/plain");
		ignoreElement(xr);
	      }
	    }
	    else if (name.equals("Content-Type")) {
	      ParameterList params = new ParameterList();
	      String value = convertResourceHeader(xr, params);
	      
	      ContentType ct = new ContentType(value);
	      ct.setParameterList(params);

	      part.addHeader(name, ct.toString());
	    }
	    else if (name.equals("Content-Disposition")) {
	      ParameterList params = new ParameterList();
	      String value = convertResourceHeader(xr, params);

	      part.addHeader(name, new ContentDisposition(value, params).toString());
	    }
	    else if (name.equals("From")) {
	      // Clean up often misspelled and misformatted header
	      ((MimeMessage) part).setFrom(convertAddressHeader(xr)[0] /* Only one allowed */);
	    }
	    else if (name.equals("Sender")) {
	      // Clean up often misspelled and misformatted header
	      ((MimeMessage) part).setSender(convertAddressHeader(xr)[0] /* Only one allowed */);
	    }
	    else if (name.equals("Reply-To")) {
	      // Clean up often misspelled and misformatted header
	      ((MimeMessage) part).setReplyTo(convertAddressHeader(xr));
	    }
	    else if (name.equals("To")) {
	      // Clean up often misspelled and misformatted header
	      ((MimeMessage) part).addRecipients(Message.RecipientType.TO, 
						 convertAddressHeader(xr));
	    }
	    else if (name.equals("Cc")) {
	      // Clean up often misspelled and misformatted header
	      ((MimeMessage) part).addRecipients(Message.RecipientType.CC, 
						 convertAddressHeader(xr));
	    }
	    else if (name.equals("Bcc")) {
	      // Clean up often misspelled and misformatted header
	      ((MimeMessage) part).addRecipients(Message.RecipientType.BCC, 
						 convertAddressHeader(xr));
	    }
	    else if (name.equals("Newsgroups")) {
	      ((MimeMessage) part).addRecipients(MimeMessage.RecipientType.NEWSGROUPS, 
						 convertAddressHeader(xr));
	    }
	    else if (name.equals("Date")) {
	      try {
		((MimeMessage) part).setSentDate(mailDateFormat.parse(convertPlainHeader(xr)));
	      }
	      catch (java.text.ParseException ex) {
		throw new XMLStreamException("Invalid date format in Date element");
	      }
	    }
	    else {
	      part.addHeader(name, convertPlainHeader(xr));
	    }
	    
	    break;
	  }

	  case END_ELEMENT:
	    System.err.println("Part end element " + xr.getLocalName());

	    if (xr.getLocalName().equals("Message")) {
	      // We're done with this part; exit
	      exit = true;
	    }
	    else {
	      throw new XMLStreamException("Unsupported MIME+XML format");
	    }

	    break;
	}
      }
    }

    protected String convertResourceHeader(XMLStreamReader xr, ParameterList params)
      throws XMLStreamException {

      for (int i = 0; i < xr.getAttributeCount(); ++i) {
	params.set(xr.getAttributeLocalName(i), xr.getAttributeValue(i));
      }

      return convertPlainHeader(xr);
    }

    protected String convertPlainHeader(XMLStreamReader xr)
      throws XMLStreamException {
      return xr.getElementText();
    }

    protected Address[] convertAddressHeader(XMLStreamReader xr)
      throws XMLStreamException {
      try {
	String value = convertPlainHeader(xr);

	return InternetAddress.parse(value);
      }
      catch (AddressException ex) {
	throw new XMLStreamException("Invalid address format: " + ex.getMessage(), ex);
      }
    }

    protected String convertTextPart(XMLStreamReader xr)
      throws XMLStreamException, MessagingException {
      return xr.getElementText();
    }

    protected Multipart convertMultiPart(XMLStreamReader xr, ContentType content_type)
      throws XMLStreamException, MessagingException {

      boolean exit         = false;
      boolean got_preamble = false;

      MimeMultipart mp = new MimeMultipart(content_type.getSubType());
      StringBuilder sb = new StringBuilder();

      while (!exit) {
	int t = xr.next();

	switch (t) {
	  case CHARACTERS:
	  case CDATA:
	  case SPACE:
	  case ENTITY_REFERENCE:
	    if (got_preamble && !xr.isWhiteSpace()) {
	      throw new XMLStreamException("Character content not allowed between " +
					   "Message elements");
	    }

	    sb.append(xr.getText());
	    break;

	  case START_ELEMENT: {
	    verifyNamespaceURI(xr);

	    if (!got_preamble) {
	      mp.setPreamble(sb.toString());
	      got_preamble = true;
	    }

	    MimeBodyPart mbp = new MimeBodyPart();

	    mp.addBodyPart(mbp);

	    convertPart(xr, mbp);
	    
	    break;
	  }

	  case END_ELEMENT:
	    if (xr.getLocalName().equals("Body")) {
	      // We're done with this part; exit
	      exit = true;
	    }
	    else {
	      throw new XMLStreamException("Unsupported MIME+XML format");
	    }
	    break;
	}
      }

      return mp;
    }

    protected void ignoreElement(XMLStreamReader xr)
      throws XMLStreamException {
      int level = 0;

      while (level >= 0) {
	switch (xr.next()) {
	  case START_ELEMENT:
	    ++level;
	    System.err.println("Element " + xr.getLocalName() + " level " + level);
	    break;

	  case END_ELEMENT:
	    --level;
	    System.err.println("/Element " + xr.getLocalName() + " level " + level);
	    break;
	}
      }
    }

    private void verifyNamespaceURI(XMLStreamReader xr)
      throws XMLStreamException {
      String nsuri = xr.getNamespaceURI();

      if (nsuri != null && !validNS.matcher(nsuri).matches()) {
	throw new XMLStreamException("Unsupported XML namespace");
      }
    }

    private static class Base64DataSource
      implements javax.activation.DataSource {
	public Base64DataSource(XMLStreamReader xr, Part part) 
	  throws XMLStreamException, MessagingException, IOException {
	  name = part.getFileName();
	  contentType = part.getContentType();
	  tempFile = File.createTempFile(XMTPParser.class.getName(), null);

	  OutputStreamWriter out = new OutputStreamWriter(
	    new Base64.OutputStream(new FileOutputStream(tempFile),
				    Base64.DECODE),
	    "iso-8859-1");

	  int    length = 1024; 
	  char[] buffer = new char[length]; 

	  for (int offset = 0, done = length; done == length; offset += length) {
	    done = xr.getTextCharacters(offset, buffer, 0, length);
	    out.write(buffer, 0, done);
	  }	  

	  out.close();
	}

	public void finalize() {
	  tempFile.delete();
	}

	public String getContentType() {
	  return contentType;
	}

	public InputStream getInputStream() 
	  throws IOException {
	  return new FileInputStream(tempFile);
	}

	public String getName() {
	  return name;
	}

	public OutputStream getOutputStream()
	  throws IOException {
	  throw new IOException("Base64DataSource is for output only");
	}

	String name;
	String contentType;
	File tempFile;
    }

    private static Pattern validNS = Pattern.compile("(^" + MIMEParser.MIME_NAMESPACE + "$)|" +
						     "(^" + MIMEParser.XMTP_NAMESPACE + "$)");

    private static MailDateFormat mailDateFormat = new MailDateFormat();
//     private static java.text.SimpleDateFormat RFC2822_DATEFORMAT =
//       new java.text.SimpleDateFormat("EEE', 'dd' 'MMM' 'yyyy' 'HH:mm:ss' 'Z", Locale.US);

    private Session session;
    private MimeMessage message;

    public static void main(String[] args) {
      try {
	XMTPParser xp = new XMTPParser();

	xp.convertMessage(new FileInputStream(args[0]));
	xp.getMessage().writeTo(System.out);

	xp.convertMessage(new FileReader(args[0]));
	xp.getMessage().writeTo(System.out);
      }
      catch (Exception ex) {
	ex.printStackTrace();
      }
    }
}
