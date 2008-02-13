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
	// We expect to see a <Message> element and nothing else

	while (xr.hasNext()) {
	  int ev = xr.nextTag();

	  switch (ev) {
	    case START_DOCUMENT:
	      break;

	    case START_ELEMENT: {
	      String lname = xr.getLocalName();

	      if (lname.equals("Message")) {
		verifyNamespaceURI(xr);

		// Accepted
		message = new MimeMessage(session);
	      
		convertPart(xr, message);
	      }
	      else {
		throw new XMLStreamException("Unsupported MIME+XML format");
	      }
	      break;
	    }

	    case END_DOCUMENT:
	      xr.close();
	      break;

	    default:
	      throw new XMLStreamException("MIME+XML parser is messed up");
	  }
	}

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

	    if (name.equals("Body")) {
	    }
	    else if (name.equals("Content-Type")) {
	      convertResourceHeader(xr, part);
	    }
	    else if (name.equals("Content-Disposition")) {
	      convertResourceHeader(xr, part);
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
	      // FIXME: use setSentDate
	      part.addHeader(name, RFC2822_DATEFORMAT.format(convertPlainHeader(xr)));
	    }
	    else {
	      part.addHeader(name, convertPlainHeader(xr));
	    }
	  }

	  case END_ELEMENT:
	    verifyNamespaceURI(xr);
	    
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

    protected void convertResourceHeader(XMLStreamReader xr, Part part)
      throws XMLStreamException {
    }

    protected String convertPlainHeader(XMLStreamReader xr)
      throws XMLStreamException {
      return null;
    }

    protected Address[] convertAddressHeader(XMLStreamReader xr)
      throws XMLStreamException {
      return null;
    }


    private void verifyNamespaceURI(XMLStreamReader xr)
      throws XMLStreamException {
      String nsuri = xr.getNamespaceURI();

      if (nsuri != null && !validNS.matcher(nsuri).matches()) {
	throw new XMLStreamException("Unsupported XML namespace");
      }
    }

    private static Pattern validNS = Pattern.compile("(^" + MIMEParser.MIME_NAMESPACE + "$)|" +
						     "(^" + MIMEParser.XMTP_NAMESPACE + "$)");

    private static java.text.SimpleDateFormat RFC2822_DATEFORMAT =
      new java.text.SimpleDateFormat("EEE', 'dd' 'MMM' 'yyyy' 'HH:mm:ss' 'Z", Locale.US);

    private Session session;
    private MimeMessage message;
}
