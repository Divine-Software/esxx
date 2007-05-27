
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

    esxx.debug.println("**** END GET HANDLER ****");

    default xml namespace = "http://www.w3.org/1999/xhtml";
    return new Response("text/html", 
	<html><body><p>Hello, world och Örjan!</p><div/></body></html>);
  }
}
