
function HTTPAuth(auth) {
  let type_params = /(\w+) +(.*)/.exec(auth);

  if (!auth || !type_params) {
    this.type = "None";
  }
  else {
    this.type   = type_params[1];
    this.params = {};

    switch (this.type) {
      case "Basic":
	[this.params.username, this.params.password] = HTTPAuth.decodeBase64(params).split(':', 2);
	break;

      case "Digest":
      case "OAuth":
      case "WRAP": {
//	let param_re = /(\w+)=(([^'"][^, ]+)|['"]([^'"]+)['"])[, ]*/g; //"'
	// Regular expression to fetch key='value', key="value" and key=value pairs
	let param_re = /(\w+)=('([^']*)'|"([^"]*)"|([^, ]*))[, ]*/g;   // '
//			|key|   |-apos|   |quote|  |-plain| |sep|
	let key_value;

	while (key_value = param_re.exec(type_params[2])) {
	  this.params[key_value[1]] = key_value[3] || key_value[4] || key_value[5];
	}

        break;
      }

      default:
	throw "Unsupported HTTP Auth type: " + this.type;
    }
  }

  for (let i in this.params) {
    esxx.log.info(i + ": " + this.params[i]);
  }
}

function HTTPAuth.decodeBase64(str) {
  with (JavaImporter(javax.mail.internet.MimeUtility, java.io)) {
    let is = MimeUtility.decode(StringBufferInputStream(str), "base64");
    return "" + new BufferedReader(new InputStreamReader(is, "UTF-8")).readLine();
  }
}
