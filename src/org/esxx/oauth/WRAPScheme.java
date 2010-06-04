
package org.esxx.oauth;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.impl.auth.RFC2617Scheme;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHeaderValueParser;
import org.apache.http.message.HeaderValueParser;
import org.apache.http.message.ParserCursor;
import org.apache.http.util.CharArrayBuffer;

public class WRAPScheme 
  extends RFC2617Scheme {    

    private boolean complete = false;

    public WRAPScheme() {
      super();
    }

    @Override public void processChallenge(Header header) 
      throws MalformedChallengeException {
      super.processChallenge(header);
      complete = true;
    }

    @Override public String getSchemeName() {
      return WRAPSchemeFactory.SCHEME_NAME;
    }

    @Override public String getRealm() {
      return null;
    }

    @Override public boolean isConnectionBased() {
      return false;
    }

    @Override public boolean isComplete() {
      return complete;
    }

    @Override protected void parseChallenge(CharArrayBuffer buffer, int pos, int len) 
      throws MalformedChallengeException {
      HeaderValueParser parser = BasicHeaderValueParser.DEFAULT;
      ParserCursor cursor = new ParserCursor(pos, buffer.length()); 
      HeaderElement[] elements = parser.parseElements(buffer, cursor);

      if (elements.length != 0) {
	throw new MalformedChallengeException("Authentication challenge contains parameters");
      }
    }

    @Override public Header authenticate(Credentials credentials, HttpRequest request) 
      throws AuthenticationException {

      if (credentials == null) {
	throw new IllegalArgumentException("No credentials");
      }

      return new BasicHeader("Authorization", 
			     getSchemeName() + 
			     " access_token=\"" + credentials.getPassword() + "\"");
    }
}
