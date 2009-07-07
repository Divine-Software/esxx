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

package org.esxx.js.protocol;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;
import org.esxx.*;
import org.esxx.js.*;
import org.esxx.xmtp.XMTPParser;
import org.esxx.util.StringUtil;
import org.mozilla.javascript.*;

public class MAILTOHandler
  extends ProtocolHandler {
  public MAILTOHandler(JSURI jsuri)
    throws URISyntaxException {
    super(jsuri);
  }

  @Override
  public Object save(Context cx, Scriptable thisObj,
		     Object data, String type, HashMap<String,String> params)
    throws Exception {
    ESXX        esxx = ESXX.getInstance();
    Properties props = jsuri.getParams(cx, jsuri.getURI());
    Session  session = Session.getInstance(props);

    String   specific = jsuri.getURI().getRawSchemeSpecificPart();
    String[] to_query = specific.split("\\?", 2);
    String   to       = StringUtil.decodeURI(to_query[0], false);

    if (type == null) {
      type = "text/plain";
    }

    if (type.equals("message/rfc822")) {
      Message msg;

      if (data instanceof String) {
	// Assume this is a real MIME message
	msg = new MimeMessage(session,
			      new ByteArrayInputStream(((String) data).getBytes("UTF-8")));
      }
      else if (data instanceof Scriptable) {
	data = esxx.serializeNode(ESXX.e4xToDOM((Scriptable) data));

	XMTPParser xmtpp  = new XMTPParser();

	msg = xmtpp.convertMessage(new StringReader((String) data));
      }
      else {
	throw new ESXXException("Unsupported data type: " + data.getClass().getSimpleName());
      }

      LinkedList<InternetAddress> recipients = 
	new LinkedList<InternetAddress>(Arrays.asList(InternetAddress.parse(to)));

      // Scan remaining headers for 'to' attributes
      if (to_query.length == 2) {
	String[] headers = to_query[1].split("&");

	for (String header : headers) {
	  String[] name_value = header.split("=", 2);

	  if (name_value.length == 2) {
	    String name  = StringUtil.decodeURI(name_value[0], false);
	    String value = StringUtil.decodeURI(name_value[1], false);

	    if (name.equalsIgnoreCase("To")) {
	      recipients.addAll(Arrays.asList(InternetAddress.parse(value)));
	    }
	  }
	}
      }

      if (recipients.isEmpty()) {
	Transport.send(msg);
      }
      else {
	Transport.send(msg, recipients.toArray(new InternetAddress[] {}));
      }
    }
    else {
      // For any other format, just send the data as-is
      Message msg = new MimeMessage(session);

      // Set To header
      msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));

      // Set remaining headers
      if (to_query.length == 2) {
	String[] headers = to_query[1].split("&");

	for (String header : headers) {
	  String[] name_value = header.split("=", 2);

	  if (name_value.length == 2) {
	    String name  = StringUtil.decodeURI(name_value[0], false);
	    String value = StringUtil.decodeURI(name_value[1], false);

	    if (name.equalsIgnoreCase("From")) {
	      msg.addFrom(InternetAddress.parse(value));
	    }
	    else if (name.equalsIgnoreCase("To")) {
	      msg.addRecipients(Message.RecipientType.TO, InternetAddress.parse(value));
	    }
	    else if (name.equalsIgnoreCase("Cc")) {
	      msg.addRecipients(Message.RecipientType.CC, InternetAddress.parse(value));
	    }
	    else if (name.equalsIgnoreCase("Bcc")) {
	      msg.addRecipients(Message.RecipientType.BCC, InternetAddress.parse(value));
	    }
	    else if (name.equalsIgnoreCase("Reply-To")) {
	      msg.setReplyTo(InternetAddress.parse(value));
	    }
	    else if (name.equalsIgnoreCase("Subject")) {
	      msg.setSubject(value);
	    }
	    else if (name.equalsIgnoreCase("Body")) {
	      if (data == null || data == Context.getUndefinedValue()) {
		data = value;
	      }
	    }
	    else {
	      msg.setHeader(name, value);
	    }
	  }
	}
      }

      if (type.equals("text/plain")) {
	msg.setText(data.toString());
      }
      else if (type.equals("text/xml")) {
	if (data instanceof Scriptable) {
	  data = esxx.serializeNode(ESXX.e4xToDOM((Scriptable) data));
	}

	msg.setDataHandler(new javax.activation.DataHandler(
          new javax.mail.util.ByteArrayDataSource((String) data, type)));
      }
      else {
	msg.setContent(data, type);
      }

      msg.setHeader("X-Mailer", "ESXX Application Server");
      msg.setSentDate(new java.util.Date());

      Transport.send(msg);
    }

    return null;
  }
}
