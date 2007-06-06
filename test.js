
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

  this.handleGet = function(req) {
    esxx.debug.println("**** START GET HANDLER ****")

    var mail = new URI("file:///home/martin/source/xcerion/xmtp/examples/Testmail-2.eml");
    return mail.load("message/rfc822")..html[0];
//     var mailto = new URI("mailto:martin@blom.org?subject=XML%20Message");
//     mailto.save(<xml>This is <empasis>XML</empasis>.</xml>, "text/xml");

    esxx.debug.println("**** END GET HANDLER ****");

    default xml namespace = "http://www.w3.org/1999/xhtml";
    return new Response("text/html", 
	<html><body><p>Hello, world och Örjan!</p><div/></body></html>);
  }
}
