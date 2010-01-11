#!/usr/bin/env esxx-js

esxx.include("sha1.js");
esxx.include("oauth.js");

var out = java.lang.System.out;

const REQUEST_TOKEN_URL = "http://twitter.com/oauth/request_token";
const ACCESS_TOKEN_URL  = "http://twitter.com/oauth/access_token";
const AUTHORIZE_URL     = "http://twitter.com/oauth/authorize";

function main() {
  let accessor = { consumerKey:    "Ow7uER0gRAtjhtoagYnow",
		   consumerSecret: "a7wZGY7bLf5yhoAXKEIADNDbxqsaqeFvUvoiuqOSOE" };

  let message = { method: "GET", 
		  action: REQUEST_TOKEN_URL,
		  parameters: {
		    oauth_signature_method: "HMAC-SHA1",
		    oauth_callback:         "oob"
		  }
		};
  OAuth.completeRequest(message, accessor);

  let request_token_uri = new URI(OAuth.addToURL(message.action, message.parameters));
  let request_token     = request_token_uri.load("application/x-www-form-urlencoded");

  accessor.token       = request_token.oauth_token;
  accessor.tokenSecret = request_token.oauth_token_secret;

  out.println("Please visit " 
	      + new URI(AUTHORIZE_URL + "?oauth_token={oauth_token}", request_token).valueOf());

  let pin = java.lang.System.console().readPassword("Enter access code: ");

  message = { method: "POST",
	      action: ACCESS_TOKEN_URL,
	      parameters: {
		oauth_signature_method: "HMAC-SHA1",
		oauth_verifier:         pin
	      }
	    };
  OAuth.completeRequest(message, accessor);

  let access_token_uri = new URI(message.action);
  let access_token     = access_token_uri.query(message.method, {},
						message.parameters, "application/x-www-form-urlencoded",
						"application/x-www-form-urlencoded");

  if (access_token.status >= 200 && access_token.status < 300) {
    let res = access_token.data;

    accessor.token       = res.oauth_token;
    accessor.tokenSecret = res.oauth_token_secret;

    out.println("Welcome, " + res.screen_name);

    let message = { method: "POST",
		    action: "http://twitter.com/saved_searches.xml",
		    parameters: {
		      oauth_signature_method: "HMAC-SHA1"
		    }
		  };
    OAuth.completeRequest(message, accessor);

    let saved = new URI(OAuth.addToURL(message.action, message.parameters));
    out.println(saved.load());
  }

  return 0;
}
