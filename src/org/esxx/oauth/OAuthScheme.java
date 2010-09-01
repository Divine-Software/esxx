/*
 * Copyright 2009 Paul Austin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.esxx.oauth;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthMessage;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.RequestLine;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.MalformedChallengeException;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.auth.RFC2617Scheme;
import org.apache.http.impl.client.RequestWrapper;
import org.apache.http.message.BasicHeader;

/**
 * @author Paul Austin
 * @author John Kristian
 */
class OAuthScheme extends RFC2617Scheme {

    private final String defaultRealm;
    /** Whether the authentication process is complete (for the current context) */
    private boolean complete;

    OAuthScheme(String defaultRealm) {
        this.defaultRealm = defaultRealm;
    }

    @Override
    public String getRealm() {
        String realm = super.getRealm();
        if (realm == null) {
            realm = defaultRealm;
        }
        return realm;
    }

    public String getSchemeName() {
        return OAuthSchemeFactory.SCHEME_NAME;
    }

    public boolean isComplete() {
        return complete;
    }

    public boolean isConnectionBased() {
        return false;
    }

    /**
     * Handle a challenge from an OAuth server.
     */
    @Override
    public void processChallenge(Header challenge) throws MalformedChallengeException {
        super.processChallenge(challenge);
        complete = true;
    }

    public Header authenticate(Credentials credentials, HttpRequest request) throws AuthenticationException {
        String uri;
        String method;
        HttpUriRequest uriRequest = getHttpUriRequest(request);
        if (uriRequest != null) {
            uri = uriRequest.getURI().toString();
            method = uriRequest.getMethod();
        } else {
            // Some requests don't include the server name in the URL.
            RequestLine requestLine = request.getRequestLine();
            uri = requestLine.getUri();
            method = requestLine.getMethod();
        }
        try {
	    InputStream body = null;

	    if (request instanceof HttpEntityEnclosingRequest) {
		HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();

		if (entity != null && entity.getContentType() != null && 
		    "application/x-www-form-urlencoded"
		    .equals(entity.getContentType().getValue())) {

		    if (!entity.isRepeatable()) {
			// Make it so
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			entity.writeTo(baos);

			ByteArrayEntity bae = new ByteArrayEntity(baos.toByteArray());
			bae.setContentEncoding(entity.getContentEncoding());
			bae.setContentType(entity.getContentType());
			bae.setChunked(entity.isChunked());

			((HttpEntityEnclosingRequest) request).setEntity(bae);
		    }
		    
		    body = entity.getContent();
		}
	    }

            OAuthMessage message = new OAuthMessage(method, uri, null, body) {
		  @Override protected void completeParameters() 
		    throws java.io.IOException {
		    String body = readBodyAsString();

		    if (body != null) {
		      addParameters(net.oauth.OAuth.decodeForm(body.trim()));
		    }
		  }
	      };

            OAuthAccessor accessor = getAccessor(credentials);
            message.addRequiredParameters(accessor);
            String authorization = message.getAuthorizationHeader(getRealm());
            return new BasicHeader("Authorization", authorization);
        } catch (Exception e) {
            throw new AuthenticationException(null, e);
        }
    }

    private HttpUriRequest getHttpUriRequest(HttpRequest request) {
        while (request instanceof RequestWrapper) {
            HttpRequest original = ((RequestWrapper) request).getOriginal();
            if (original == request) {
                break;
            }
            request = original;
        }
        if (request instanceof HttpUriRequest) {
            return (HttpUriRequest) request;
        }
        return null;
    }

    private OAuthAccessor getAccessor(Credentials credentials) {
        if (credentials instanceof OAuthCredentials) {
            return ((OAuthCredentials) credentials).getAccessor();
        }
        return new OAuthAccessor(new OAuthConsumer(null // callback URL
                , credentials.getUserPrincipal().getName() // consumer key
                , credentials.getPassword() // consumer secret
                , null)); // service provider
    }

}
