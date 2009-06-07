
esxx.include("src/Blog.js");

XML.ignoreWhitespace = false;
XML.prettyPrinting   = false;

var blog = new Blog("jdbc:h2:mem:Blog;DB_CLOSE_DELAY=-1", "admin", "admin");
