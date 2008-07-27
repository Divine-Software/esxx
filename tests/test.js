
default xml namespace = "http://www.w3.org/1999/xhtml";

function MyApp(e) {
  this.local = "an instance variable";
}

MyApp.prototype.handleError = function(req, ex) {
  esxx.log.debug("**** START ERROR HANDLER ****");
  esxx.log.info(ex);
  esxx.log.debug("**** END ERROR HANDLER ****");
  return <html><body>{ex}</body></html>
}

MyApp.prototype.handleGet = function(req) {
  req.log.debug("**** START GET HANDLER ****")

//   var ldap = new URI("ldap://ldap.blom.org/ou=People,dc=blom,dc=org??sub?(sn=Blom)");
  // var ldap = new URI("ldap://ldap.blom.org/ou=Groups,dc=blom,dc=org??sub");

//   URI.prototype.auth.push({
//         username: "uid=martin,ou=People,dc=blom,dc=org",
// 	password: "****", 
// 	mechanism: "simple" });
//   req.log.info(ldap.load());

  //     var db = new URI("jdbc:postgresql:esxx?user=esxx&password=secret");
    
  //     return db.query("select * from customers where country = {c}", {
  // 	c : "Sweden"
  // 	  });

  //     var mail = new URI("Testmail-3.eml");
  //     return mail.load("message/rfc822; x-format=esxx;x-process-html=false");
  //     var mailto = new URI("mailto:martin@blom.org?subject=XML%20Message");
  //     mailto.save(<xml>This is <empasis>XML</empasis>.</xml>, "text/xml");

  req.log.debug("**** END GET HANDLER ****");

  return new Response(200, null,
		      <html><body><p>Hello, world och Örjan!</p><div/></body></html>,
		      "text/html");
};

MyApp.prototype.xsltCallback = function() {
  return "Result from MyApp.xsltCallback: " + this.local;
};
