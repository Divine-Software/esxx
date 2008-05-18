
var started = new Date();

esxx.debug.println("Initialization code is executed only once.");
esxx.debug.println("Edit or 'touch' any included file to force reinitialization.");
esxx.debug.println();

function handleGet() {
  var now = new Date();

  esxx.debug.println("Request handler code is executed on every request.");
  esxx.debug.println();
  esxx.debug.println("The application was started on " + started);
  esxx.debug.println("This request was processed on " + now);

  // Return a dummy XML document
  return <see-the-esxx-debug-log/>;
}
