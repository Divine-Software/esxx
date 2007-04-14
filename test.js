

for (var h in esxx.headers) {
  esxx.debug.println(h + ": " + esxx.headers[h] + " (" + typeof esxx.headers[h] + ")");
}

for (var h in esxx.properties) {
  esxx.debug.println(h + ": " + esxx.properties[h] + " (" + typeof esxx.properties[h] + ")");
}

esxx.debug.println(esxx.headers.Status);
esxx.headers[0] =  "Apa: kalle";
esxx.headers[1] =  "Apa2; kalle";

esxx.debug.println(esxx.document);
default xml namespace = new Namespace("http://martin.blom.org/esxx/1.0/");
esxx.debug.println(esxx.document.info.author[0]);
default xml namespace = new Namespace("");

function MyApp(e) {

  this.handleError = function(ex) {
	esxx.debug.println("**** START ERROR HANDLER ****");
	esxx.debug.println(ex);
	esxx.debug.println("**** END ERROR HANDLER ****");
    };

  this.handleGet = function() {
    esxx.headers.Status = "201 OK";
    esxx.debug.println("**** START GET HANDLER ****")

    for (i in esxx.query) {
      esxx.debug.println("i: " + esxx.query[i]);
    }

    var url = new URL("test-code.esxx");
    esxx.debug.println(url);
    var filen = url.loadXML();
    esxx.debug.println(typeof filen);

    XML.ignoreComments = false;

    a = <apa>data<a/> <!-- apa --></apa>;

    XML.prettyPrinting = false;
    esxx.debug.println(a);

    XML.prettyPrinting = true;
    esxx.debug.println(a);

    default xml namespace = new Namespace("http://www.w3.org/1999/xhtml");

    var html = <html>
    <body><p>Hej här är en banan till dig!</p>
    </body>
    </html>;

    html.body.*[0] = <address name="Martin Blom"/> + html.body.*[0];
    html.body.* += <address name="Martin Blom"/>;

    esxx.debug.println("**** END GET HANDLER ****");

    return html;
  }
}
