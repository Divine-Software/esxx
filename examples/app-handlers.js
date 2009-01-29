
function App() {
  this.stock = new Stock();
};

function App.prototype.onError(req, err) {
  return <error>
      <title>Something went wrong</title>
      <message>{err.message}</message>
    </error>;
}

function App.prototype.handleGET(req) {
  let soap_uri = new URI(req.scriptURI, "soap").valueOf();

  return <services>
      <service type="HTML" name="Main Index" href={req.scriptURI.valueOf()}/>
      <service type="SOAP" name="Stock Quote" href={soap_uri}/>
    </services>;
}

function Stock() {
}

function Stock.prototype.getQuote(req, body, hdr) {
  req.log.info("Getting quote for " + body.symbol);

  return <Result>{Math.round(Math.random() * 10000) / 100}</Result>;
}

var app = new App();
