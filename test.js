
var GLOB = "kalle";

function MyApp(e) {
  this.inside_my = "";

  this.handleError = function(ex) {
	esxx.debug.println("**** START ERROR HANDLER ****");
	esxx.debug.println(ex);
	esxx.debug.println("**** END ERROR HANDLER ****");
	return <html><body>{ex}</body></html>
    };

  var FUN = "kalle";

  this.handleGet = function() {
    var local = "t%";

    esxx.headers.Status = "201 OK";
    esxx.debug.println("**** START GET HANDLER ****")

//     var ldap = new URI("ldap://blom.org:389/uid=martin,ou=People,dc=blom,dc=org??base?(objectClass=*)");

//     esxx.debug.println(ldap.load());
//     esxx.debug.println(new URI("build").load());
    
    var db = new URI("jdbc:postgresql:martin?user=martin&password=martin");
//    db.user = "martin";
//     db.password = "martin";

    esxx.debug.println(db.query("select * from test where name like ? or apan = ?", 
				local, "40"));

//     var uri = new URI("http://martin.blom.org/");
//     esxx.debug.println(uri.load());

//     var uri = new URI("http://www.foi.se/upload/GD_portr%C3%A4tt.jpg");
//     esxx.debug.println(uri.load("image/*"));

//     var mailto1 = new URI("mailto:addr1%2C%20addr2");
//     var mailto2 = new URI("mailto:?to=addr1%2C%20addr2");
//     var mailto3 = new URI("mailto:addr1?to=addr2");

//     new URI("mailto:gorby%25kremvax@example.com").save("");
//     new URI("mailto:unlikely%3Faddress@example.com?blat=foop").save("");

//     mailto1.save("Hej");
//     mailto2.save("Hej");
//     mailto3.save("Hej");

//     var url = "mailto:martin@blom.org"
//     url += "?bcc=" + encodeURIComponent("lcs@lysator.liu.se");
//     url += "&from=" + encodeURIComponent("Martin Blom <martin@blom.org>");
//     url += "&subject=" + encodeURIComponent("Hej Banan från ESXX");
//     url += "&Body=" + encodeURIComponent("Default Body");

//     var mailto = new URI(url);
// //    mailto.save("Hej alla barn nu ska vi röka på lite");
//     mailto.save(<html>Hallå!</html>, "text/xml");

    esxx.debug.println("**** END GET HANDLER ****");

    default xml namespace = "http://www.w3.org/1999/xhtml";
    return ["text/xml; charset=ISO-8859-1", <p>Hello, world!</p>];
  }
}
