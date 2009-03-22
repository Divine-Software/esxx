
esxx.include("src/Blog.js");

XML.ignoreWhitespace = false;
XML.prettyPrinting   = false;

var blog = new Blog("jdbc:h2:Blog", "admin", "admin");
