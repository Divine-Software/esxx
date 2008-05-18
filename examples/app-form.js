/** The web application. **/

// An obvious rip-off of "Developing a Spring Framework MVC application step-by-step"
// (http://www.springframework.org/docs/MVC-step-by-step/Spring-MVC-step-by-step.html).
// I just wanted to find out how a similar example would look like in ESXX.

function App(productMmanager) {
  this.productManager = productMmanager;
}

function App.prototype.redirectToMain(req) {
  /** This function sends a (relative and thus, strictly speaking,
   * invalid) redirect response, making the client fetch the front
   * page. */

  return [303, null, null, {Location: req.env.SCRIPT_NAME + "/main"}];
}

function App.prototype.getIndex(req) {
  /** This is the front page. Presents welcome information and the product list. **/
  var now = new Date();

  return <welcome>
    <now>{new Date()}</now>
    {this.productManager.getProducts()}
  </welcome>;
}


function App.prototype.increasePricesForm(req, errorMessage) {
  /** This function displays the "Price Increase" form. It is called
   * both by ESXX (with one single argument) and directly from
   * increasePricesAction(). **/

  // If errorMessage is provided, make an XML element out of it, else
  // set it to the empty string
  errorMessage = errorMessage && <errorMessage>{errorMessage}</errorMessage> || "";

  // Display the "Price Increase" form
  return <increasePrices>
    <percentageValue>
      {req.query.percentageValue || esxx.document.increase.@def}
    </percentageValue>
    {errorMessage}
  </increasePrices>;
}

function App.prototype.increasePricesAction(req) {
  /** This function handles of the "Price Increase" form data **/
  var increase = parseFloat(req.query.percentageValue);
  var min      = esxx.document.increase.@min;
  var max      = esxx.document.increase.@max;

  // Validate form input
  if (!req.query.percentageValue) {
    return this.increasePricesForm(req, "Percentage not specified!!!");
  }
  else if (isNaN(increase)) {
    return this.increasePricesForm(req, "That is not a number!!!");
  }

  if (increase <= min) {
    return this.increasePricesForm(req, "You have to specify a percentage higher than " + min);
  }
  else if (increase > max) {
    return this.increasePricesForm(req, "Don't be greedy - you can't raise prices by more than " + max + "%!");
  }

  this.productManager.increasePrice(increase);

  // Redirect back to main page (using GET)
  return this.redirectToMain(req);
}
