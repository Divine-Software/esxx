
function MyApp(e) {

  this.handleError = function(ex) {
	esxx.debug.println("**** START ERROR HANDLER ****");
	esxx.debug.println(ex);
	esxx.debug.println("**** END ERROR HANDLER ****");
	return <html><body>{ex}</body></html>
    };

  this.handleGet = function() {
    esxx.headers.Status = "201 OK";
    esxx.debug.println("**** START GET HANDLER ****")

    var directory = new URL("..").loadXML();

    esxx.debug.println("File larger than 2000 bytes:");
    esxx.debug.println(directory.file.(@length > 2000));

    esxx.debug.println("Files starting with the letter L:");
//    esxx.debug.println(directory.file.(/^L/.test(@name)));
    esxx.debug.println(directory.file.(@name.match("^L")));

    esxx.debug.println(new Date(0).toString());


    var url = new URL("/opt/Media/martin/xcerion/server/db/limedesk/My%20Documents?hello");

    esxx.debug.println(url.loadXML());

    esxx.debug.println("**** END GET HANDLER ****");

    return <html/>;
  }
}
