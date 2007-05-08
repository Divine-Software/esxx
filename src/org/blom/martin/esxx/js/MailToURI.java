
package org.blom.martin.esxx.js;

import java.net.URI;
import java.util.HashMap;
import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;
import org.blom.martin.esxx.ESXX;
import org.mozilla.javascript.*;

public class MailToURI 
  extends JSURI {
    public MailToURI(ESXX esxx, URI uri) {
      super(esxx, uri);
    }

    protected Object load(Context cx, Scriptable thisObj, 
			String type, HashMap<String,String> params)
      throws Exception {
      throw Context.reportRuntimeError("URI protocol '" + uri.getScheme() + 
				       "' does not support load()."); 
    }

    protected Object query(Context cx, Scriptable thisObj,
			   String query, String type, HashMap<String,String> params)
      throws Exception {
      throw Context.reportRuntimeError("URI protocol '" + uri.getScheme() + 
				       "' does not support query() (yet)."); 
    }

    protected Object save(Context cx, Scriptable thisObj, 
			  Object data, String type, HashMap<String,String> params)
      throws Exception {
      Properties props = getProperties(thisObj);

      if (props.getProperty("mail.smtp.host") == null) {
	props.setProperty("mail.smtp.host", props.getProperty("host", "localhost"));
      }

      if (props.getProperty("mail.smtp.user") == null && 
	  props.getProperty("user") != null) {
	props.setProperty("mail.smtp.user", props.getProperty("user"));
      }

      Session session = Session.getDefaultInstance(props);

      Message msg = new MimeMessage(session);

      String   specific = uri.getRawSchemeSpecificPart();
      String[] to_query = specific.split("\\?", 2);
      String   to       = JSURI.decodeURI(to_query[0], false);

      // Set To header
      msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));

      // Set remaining headers
      if (to_query.length == 2) {
	String[] headers = to_query[1].split("&");

	for (String header : headers) {
	  String[] name_value = header.split("=", 2);

	  if (name_value.length == 2) {
	    String name  = JSURI.decodeURI(name_value[0], false);
	    String value = JSURI.decodeURI(name_value[1], false);

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

      if (type == null || type.equals("text/plain")) {
	msg.setText(data.toString());
      }
      else if (type.equals("text/xml")) {
	if (data instanceof Scriptable) {
	  data = esxx.serializeNode(esxx.e4xToDOM((Scriptable) data), true);
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
      return null;
    }
}
