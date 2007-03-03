

for (var h in esxx.headers) {
  esxx.error.println(h + ": " + esxx.headers[h] + " (" + typeof esxx.headers[h] + ")");
}

esxx.error.println(esxx.headers.Status);

esxx.error.println(esxx.document);
