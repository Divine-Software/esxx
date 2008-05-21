
var started  = new Date();
var firstrun = true;

function handleGet(req) {
  var now = new Date();

  if (firstrun) {
    // (Real code would protect this code with esxx.sync(), since
    // several requests may be executing at once)
    firstrun = false;

    req.log.info("Initialization code is executed only once.");
    req.log.info("Edit or 'touch' any included file to force reinitialization.");
    req.log.info("");
  }

  req.log.info("Request handler code is executed on every request.");
  req.log.info("");
  req.log.info("The application was started on " + started);
  req.log.info("This request was processed on " + now);

  // Return a dummy XML document
  return <see-the-esxx-debug-log/>;
}
