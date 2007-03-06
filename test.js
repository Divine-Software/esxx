

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
    XML.prettyPrinting = false;

    a = <?xml version="1.0"?>
        <!DOCTYPE html PUBLIC "-//W3C/DTD HTML 3.0 Transitional//EN" "">

    <!-- apa -->
    <html>data<a/>
    </html>;

    esxx.error.println("**** END GET HANDLER ****");

//     var html = <!DOCTYPE html PUBLIC "-//W3C/DTD HTML 3.0 Transitional//EN" "" [
//       <!ENTITY nbsp '&#xa0;'>
//       ]>
//     <html>
//     <body><p>Hej här är en banan till&nbsp;dig!</p>
//     </body>
//     </html>;

//     html.body.*[0] = <address name="Martin Blom"/> + html.body.*[0];
//     html.body.* += <address name="Martin Blom"/>;

    return esxx.document;
  }
}



default xml namespace = new Namespace("http://martin.blom.org/esxx/1.0/");
esxx.error.println(esxx.document.info.author[0]);
//default xml namespace = new Namespace("");
