
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

//     var uri = new URI("http://martin.blom.org/");
//     esxx.debug.println(uri.load());

//     var mailto1 = new URI("mailto:addr1%2C%20addr2");
//     var mailto2 = new URI("mailto:?to=addr1%2C%20addr2");
//     var mailto3 = new URI("mailto:addr1?to=addr2");

//     new URI("mailto:gorby%25kremvax@example.com").save("");
//     new URI("mailto:unlikely%3Faddress@example.com?blat=foop").save("");

//     mailto1.save("Hej");
//     mailto2.save("Hej");
//     mailto3.save("Hej");

    var url = "mailto:martin@blom.org"
    url += "?bcc=" + encodeURIComponent("lcs@lysator.liu.se");
    url += "&from=" + encodeURIComponent("Martin Blom <martin@blom.org>");
    url += "&subject=" + encodeURIComponent("Hej Banan från ESXX");
    url += "&Body=" + encodeURIComponent("Default Body");

    var mailto = new URI(url);
//    mailto.save("Hej alla barn nu ska vi röka på lite");
    mailto.save(<html>Hallå!</html>, "text/xml");

    esxx.debug.println("**** END GET HANDLER ****");

    return <db/>;
  }
}
