
function MyApp(e) {

  this.handleError = function(ex) {
	esxx.debug.println("**** START ERROR HANDLER ****");
	esxx.debug.println(ex);
	esxx.debug.println("**** END ERROR HANDLER ****");
	return <html><body>{ex}</body></html>
    };

  this.handleGet = function() {
    esxx.headers.Status = "201 OK";
    esxx.debug.println("**** START GET HANDLER ****")

//     var ldap = new URI("ldap://blom.org:389/uid=martin,ou=People,dc=blom,dc=org??base?(objectClass=*)");

//     esxx.debug.println(ldap.load());
//     esxx.debug.println(new URI("build").load());
    
//     java.lang.Class.forName("org.postgresql.Driver");
//     var db = new URI("jdbc:postgresql:martin");
//     db.user = "martin";
//     db.password = "martin";
//     esxx.debug.println(db.query("select * from test"));

    var url = new URI("cp.xml");
    esxx.debug.println(url.load());

    esxx.debug.println("**** END GET HANDLER ****");

    return <db/>;
  }
}
