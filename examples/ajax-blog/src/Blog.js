
esxx.include("BlogDB.js");

XML.ignoreWhitespace = false;
XML.prettyPrinting   = false;

function Blog(dburi) {
  if (!dburi) {
    throw "Blog(dburi): dburi missing.";
  }

  this.db = new BlogDB(dburi);

  // Create the database, if missing, and add first post
  if (!this.db.checkDB()) {
    esxx.log.info("Creating Blog database " + dburi);
    this.db.createDB();

    let one = this.db.addPost("First post", 
			      <>
			      Oh my, this <strong>ESXX</strong> thing 
			      is <em>really</em> something!
			      </>.toXMLString());
    this.db.addComment(one, "Sure is.");
    this.db.addComment(one, "Yep, it really rocks!");
  }
}

function Blog.prototype.setXSLTParams(req, next) {
  let res = next();

  res.params.scriptURI   = req.scriptURI;
  res.params.adminURI    = new URI(req.scriptURI, "admin.html");
  res.params.postsURI    = new URI(req.scriptURI, "posts/");
  res.params.resourceURI = new URI(req.scriptURI, "..");

  return res;
}

function Blog.prototype.renderBlog(req) {
  // Gimme 10 posts
  let posts = Blog.fixResponse(req, this.db.listPosts(10), true);

  return <blog>
           {posts}
         </blog>;
}

function Blog.prototype.renderPost(req) {
  let post = this.db.getPost(req.args.post_id);

  if (post.length() == 0) {
    return [ESXX.Response.NOT_FOUND, {},
	    <error>Post {req.args.post_id} not found.</error>];
  }

  let comments = this.db.listComments(req.args.post_id, req.query.limit || 100);

  return <blog-entry>
           {Blog.fixResponse(req, post, true)}
           {Blog.fixResponse(req, comments, true)}
         </blog-entry>;
}


function Blog.prototype.renderAdminGUI(req) {
  return <admin/>;
}


function Blog.prototype.listPosts(req) {
  let posts = this.db.listPosts(req.query.limit || 100);

  return Blog.fixResponse(req, posts, false);
}

function Blog.prototype.addPost(req) {
  let title = req.message.title.toString();
  let body  = req.message.body.toString();

  if (req.contentType != "application/xml") {
    return [ESXX.Response.UNSUPPORTED_MEDIA_TYPE, {},
	    <error>Posts must be submitted as 'application/xml'.</error>];
  }

  if (!title || !body) {
    return [ESXX.Response.UNPROCESSABLE_ENTITY, {},
	    <error>Posts must have a non-empty title and body.</error>];
  }

  let post_id = this.db.addPost(title, body);

  return [ESXX.Response.CREATED, { Location: Blog.getPostLocation(req, post_id, false) }];
}

function Blog.prototype.getPost(req) {
  let post = this.db.getPost(req.args.post_id);

  if (post.length() == 0) {
    return [ESXX.Response.NOT_FOUND, {},
	    <error>Post {req.args.post_id} not found.</error>];
  }

  return Blog.fixResponse(req, post, false);
}

function Blog.prototype.updatePost(req) {
  if (req.contentType != "application/xml") {
    return [ESXX.Response.UNSUPPORTED_MEDIA_TYPE, {},
	    <error>Posts must be submitted as 'application/xml'.</error>];
  }

  let title = req.message.title.toString();
  let body  = req.message.body.*.toXMLString();

  if (!title || !body) {
    return [ESXX.Response.UNPROCESSABLE_ENTITY, {},
	    <error>Posts must have a non-empty title and body.</error>];
  }

  if (!this.db.updatePost(req.args.post_id, title, body)) {
    return [ESXX.Response.NOT_FOUND, {},
	    <error>Post {req.args.post_id} not found.</error>];
  }

  return ESXX.Response.NO_CONTENT;
}

function Blog.prototype.deletePost(req) {
  if (!this.db.deletePost(req.args.post_id)) {
    return [ESXX.Response.NOT_FOUND, {},
	    <error>Post {req.args.post_id} not found.</error>];
  }

  return ESXX.Response.NO_CONTENT;
}


function Blog.prototype.listComments(req) {
  let comments = this.db.listComments(req.args.post_id, req.query.limit || 100);

  return Blog.fixResponse(req, comments, false);
}

function Blog.prototype.addComment(req) {
  let body = req.message.body.toString();

  if (req.contentType != "application/xml") {
    return [ESXX.Response.UNSUPPORTED_MEDIA_TYPE, {},
	    <error>Comments must be submitted as 'application/xml'.</error>];
  }

  if (!body) {
    return [ESXX.Response.UNPROCESSABLE_ENTITY, {},
	    <error>Comments must have a non-empty body.</error>];
  }

  let comment_id = this.db.addComment(req.args.post_id, body);

  return [ESXX.Response.CREATED, {
    Location: Blog.getCommentLocation(req, req.args.post_id, comment_id, false)
  }];
}

function Blog.prototype.getComment(req) {
  let comment = this.db.getComment(req.args.post_id, req.args.comment_id);

  if (comment.length() == 0) {
    return [ESXX.Response.NOT_FOUND, {},
	    <error>Comment {req.args.comment_id} to post {req.args.post_id} not found.</error>];
  }

  return Blog.fixResponse(req, comment, false);
}

function Blog.prototype.updateComment(req) {
  if (req.contentType != "application/xml") {
    return [ESXX.Response.UNSUPPORTED_MEDIA_TYPE, {},
	    <error>Comments must be submitted as 'application/xml'.</error>];
  }

  let body = req.message.body.*.toXMLString();

  if (!body) {
    return [ESXX.Response.UNPROCESSABLE_ENTITY, {},
	    <error>Comments must have a non-empty body.</error>];
  }

  if (!this.db.updateComment(req.args.post_id, req.args.comment_id, body)) {
    return [ESXX.Response.NOT_FOUND, {},
	    <error>Comment {req.args.comment_id} to post {req.args.post_id} not found.</error>];
  }

  return ESXX.Response.NO_CONTENT;
}

function Blog.prototype.deleteComment(req) {
  if (!this.db.deleteComment(req.args.post_id, req.args.comment_id)) {
    return [ESXX.Response.NOT_FOUND, {},
	    <error>Comment {req.args.comment_id} to post {req.args.post_id} not found.</error>];
  }

  return ESXX.Response.NO_CONTENT;
}

function Blog.getPostLocation(req, post_id, html) {
  return new URI(req.scriptURI, "posts/{p}" + (html ? ".html" : ""), {
		   p: post_id
		 }).valueOf();
}

function Blog.getCommentLocation(req, post_id, comment_id, html) {
  return new URI(req.scriptURI, "posts/{p}/{c}" + (html ? ".html" : ""), {
		   p: post_id,
		   c: comment_id
		 }).valueOf();
}

function Blog.fixResponse(req, xml, html) {
  delete xml.@resultSet; // We don't want this

  switch (xml.localName()) {
    case "posts":
      // Add URIs to all posts
      for each (let post in xml.post) {
	Blog.fixResponse(req, post, html);
      }
      break;

    case "post": {
      xml.@href = Blog.getPostLocation(req, xml.id, html);
      xml.body.* = new XMLList(xml.body.toString());
      break;
    }

    case "comments":
      for each (let comment in xml.comment) {
	Blog.fixResponse(req, comment, html);
      }
      break;

    case "comment":
      xml.@href = Blog.getCommentLocation(req, xml.post_id, xml.id, html);
      xml.body.* = new XMLList(xml.body.toString());
      break;
  }

  return xml;
}

