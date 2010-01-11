
esxx.include("HTTPAuth.js")

function OAuthServer(db_uri) {
  this.db = new URI(db_uri);

  // Create DB
  let sql = new URI("oauth-server.sql").load("text/plain");

  for each (let s in sql.split(';')) {
    this.db.query(s);
  }
}

function OAuthServer.prototype.authFilter(req, next) {
  let auth = new HTTPAuth(req.headers.Authorization);

  if (auth.type == "None") {
    let realm = this.db.query("SELECT realm FROM properties").entry.realm;

    return [ESXX.Response.UNAUTHORIZED, {
      "WWW-Authenticate": "Digest realm=" + realm
    }, "Please provide valid credentials to log in."];
  }
  else {
    return next();
  }
}

function OAuthServer.prototype.showMainPage(req) {
  return <main-page/>;
}

function OAuthServer.prototype.showConsumerRegForm(req) {
  return <consumer-form/>;
}

function OAuthServer.prototype.procConsumerRegForm(req) {
  if (!req.message.name) {
    return <consumer-form>
      <error type="name-missing"/>
    </consumer-form>;
  }

//  db.append(<consumers>
//	    <name>{name}</name>
//	    <key>{key}</key>
//	    <secret>{secret}</secret>
//	    </consumer>);

//  db.append({ $table: "consumers", name: name, key: key, secret: secret });

//  new URI(this.db, "#consumers").append({ name: name, key: key, secret: secret });
//  new URI(this.db, "#consumers;id={entry}", { entry: 10}).save({ 

//  this.db.query("insert into consumers (name, key, secret) " +
//		"values ({0}, {1}, {2})",
//		[name, key, secret]);
}



function OAuthServer.prototype.issueRequestToken(req) {
}
