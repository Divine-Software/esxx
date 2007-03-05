

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
    esxx.error.println("**** GET HANDLER ****");
    a = new XML(<!-- apa --><x><html>data<a/></html></x>);
    return a;
  }
}

default xml namespace = new Namespace("http://martin.blom.org/esxx/1.0/");
esxx.error.println(esxx.document.info.author[0]);
