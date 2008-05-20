
var started = new Date();
var message = "Initialization code is executed only once.\n" +
              "Edit or 'touch' any included file to force reinitialization.\n\n";

function handleGet(req) {
  var now = new Date();

  if (message) {
    // (Real code would protect this block with esxx.sync(), since
    // several requests may be executing at once)

    req.log.info(message);
    message = null;
  }

  req.log.info("Request handler code is executed on every request.");
  req.log.info("");
  req.log.info("The application was started on " + started);
  req.log.info("This request was processed on " + now);

  // Return a dummy XML document
  return <see-the-esxx-debug-log/>;
}
