
package org.esxx.oauth;

import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthSchemeFactory;
import org.apache.http.params.HttpParams;

public class WRAPSchemeFactory 
  implements AuthSchemeFactory {    
    public static final String SCHEME_NAME = "WRAP";

    public AuthScheme newInstance(final HttpParams params) {
      return new WRAPScheme();
    }
}
