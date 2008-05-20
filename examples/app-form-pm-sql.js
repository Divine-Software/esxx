/** The product manager class, that holds all products (SQL implementation). **/

function ProductManager(products) {
  this.db = new URI(esxx.document.db.@uri);

  try {
    // Run a test query
    this.db.query("SELECT id from products WHERE id = 0");
  }
  catch (e) {
    // On errors, assume there was no previous database
    this.setProducts(products);
  }
}

function ProductManager.prototype.setProducts(products) {
  /** Re-create and populate the 'products' table **/

  try {
    this.db.query("DROP TABLE products");
  }
  catch (e) {
    esxx.log.info("No previous 'products' table.");
  }

  this.db.query("CREATE TABLE products (id INTEGER NOT NULL PRIMARY KEY, \
                                        description VARCHAR(255), \
                                        price DECIMAL(15,2))");
  this.db.query("CREATE INDEX products_description ON products(description)");

  for each (p in products.product) {
    this.db.query("INSERT INTO products (id, description, price) \
                   VALUES ({id}, {description}, {price})", p);
  }
}

function ProductManager.prototype.getProducts() {
  /** Return all products as an XML document (column 'id' omitted) **/

  return this.db.query("SELECT description, price FROM products",
		       {$result: "products", $entry: "product"});
}

function ProductManager.prototype.increasePrice(pct) {

  this.db.query("UPDATE products SET price = price * (100 + {percent}) / 100",
		{ percent: pct });
}
