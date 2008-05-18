/** The product manager class, that holds all products (XML implementation). **/

function ProductManager(products) {
  this.products = products || new XML();
}

function ProductManager.prototype.setProducts(products) {
  this.products = products;
}

function ProductManager.prototype.getProducts() {
  return this.products;
}

ProductManager.prototype.increasePrice = esxx.sync(function(pct) {
  for each (var product in this.products.*) {
    product.price = product.price * (100 + pct) / 100;
  }
});
