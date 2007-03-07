

for (var h in esxx.headers) {
  esxx.error.println(h + ": " + esxx.headers[h] + " (" + typeof esxx.headers[h] + ")");
}

esxx.error.println(esxx.headers.Status);

esxx.error.println(esxx.document);

function MyApp(e) {

  this.handleError = function(ex) {
	esxx.error.println("**** START ERROR HANDLER ****");
	esxx.error.println(ex);
	esxx.error.println("**** END ERROR HANDLER ****");
    };

  this.handleGet = function() {
    esxx.error.println("**** START GET HANDLER ****");

    XML.ignoreComments = false;

    a = <apa>data<a/> <!-- apa --></apa>;

    XML.prettyPrinting = false;
    esxx.error.println(a);

    XML.prettyPrinting = true;
    esxx.error.println(a);

    var html = <html>
    <body><p>Hej här är en banan till dig!</p>
    </body>
    </html>;

    html.body.*[0] = <address name="Martin Blom"/> + html.body.*[0];
    html.body.* += <address name="Martin Blom"/>;

    esxx.error.println("**** END GET HANDLER ****");

    return html;
//    return esxx.document;
  }
}



default xml namespace = new Namespace("http://martin.blom.org/esxx/1.0/");
esxx.error.println(esxx.document.info.author[0]);
default xml namespace = new Namespace("");
