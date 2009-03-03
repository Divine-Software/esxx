
esxx.include("BlogDB.js");

function Blog(dburi) {
  if (!dburi) {
    throw "Blog(dburi): dburi missing.";
  }

  this.db = new BlogDB(dburi);

  // Create the database, if missing
  if (!this.db.checkDB()) {
    esxx.log.info("Creating Blog database " + dburi);
    this.db.createDB();

    let one = this.db.addPost("t1", "b1");
    this.db.addPost("t2", "En <b>bra</b>-post");
    this.db.addComment(one, "c1");
    this.db.addComment(one, "c2");
  }
}

function Blog.getPostLocation(req, post_id) {
  return new URI(req.scriptURI, "posts/{p}", {
		   p: post_id
		 }).valueOf();
}

function Blog.getCommentLocation(req, post_id, comment_id) {
  return new URI(req.scriptURI, "posts/{p}/{c}", {
		   p: post_id,
		   c: comment_id
		 }).valueOf();
}

function Blog.fixAttributes(req, xml) {
  delete xml.@resultSet; // We don't want this

  switch (xml.localName()) {
    case "posts":
      // Add URIs to all posts
      for each (let post in xml.post) {
	Blog.fixAttributes(req, post);
      }
      break;

    case "post":
      xml.@href = Blog.getPostLocation(req, xml.id);
      break;

    case "comments":
      for each (let comment in xml.comment) {
	Blog.fixAttributes(req, comment);
      }
      break;

    case "comment":
      xml.@href = Blog.getCommentLocation(req, xml.post_id, xml.id);
      break;
  }

  return xml;
}

function Blog.prototype.listPosts(req) {
  let posts = this.db.listPosts(req.query.limit || 100);

  return Blog.fixAttributes(req, posts);
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

  return [ESXX.Response.CREATED, { Location: Blog.getPostLocation(req, post_id) }];
}

function Blog.prototype.getPost(req) {
  let post = this.db.getPost(req.args.post_id);

  if (post.length() == 0) {
    return [ESXX.Response.NOT_FOUND, {},
	    <error>Post {req.args.post_id} not found.</error>];
  }

  return Blog.fixAttributes(req, post);
}

function Blog.prototype.updatePost(req) {
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

  return Blog.fixAttributes(req, comments);
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
	    Location: Blog.getCommentLocation(req, req.args.post_id, comment_id)
	  }];
}

function Blog.prototype.getComment(req) {
  let comment = this.db.getComment(req.args.post_id, req.args.comment_id);

  if (comment.length() == 0) {
    return [ESXX.Response.NOT_FOUND, {},
	    <error>Comment {req.args.comment_id} to post {req.args.post_id} not found.</error>];
  }

  return Blog.fixAttributes(req, comment);
}

function Blog.prototype.updateComment(req) {
  let body = req.message.body.toString();

  if (req.contentType != "application/xml") {
    return [ESXX.Response.UNSUPPORTED_MEDIA_TYPE, {},
	    <error>Comments must be submitted as 'application/xml'.</error>];
  }

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
