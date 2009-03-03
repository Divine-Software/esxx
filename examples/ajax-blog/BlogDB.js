
function BlogDB(dburi) {
  this.dbURI = new URI(dburi);
}

function BlogDB.prototype.checkDB() {
  return this.dbURI.query("SELECT count(*) AS cnt " +
			  "FROM information_schema.tables " +
			  "WHERE table_name = 'POSTS' OR table_name = 'COMMENTS'").entry.cnt == 2;
}

function BlogDB.prototype.recreateDB() {
  this.destroyDB();
  this.createDB();
}

function BlogDB.prototype.createDB() {
  this.dbURI.query("create table posts ("
		   + "id identity not null, "
		   + "title varchar not null, "
		   + "body varchar not null, "
		   + "created timestamp not null, "
		   + "updated timestamp not null, "
		   + "primary key (id) "
		   + ")");

  this.dbURI.query("create table comments ("
		   + "id identity not null, "
		   + "post_id int not null, "
		   + "body varchar not null, "
		   + "created timestamp not null, "
		   + "updated timestamp not null, "
		   + "foreign key (post_id) references posts (id) on delete cascade"
		   + ")");
}

function BlogDB.prototype.destroyDB() {
  this.dbURI.query("drop table posts if exists ");
  this.dbURI.query("drop table comments if exists");
}

function BlogDB.prototype.addPost(title, body) {
  let res = this.dbURI.query("insert into posts (title, body, created, updated) "
			     + "values ({t}, {b}, NOW(), NOW())", {
			       t: title,
			       b: body
			     });
  return parseInt(res.entry.identity);
}

function BlogDB.prototype.getPost(id) {
  return this.dbURI.query(
    "select *, (select count(*) from comments where post_id = {i}) as comments "
      + "from posts where id = {i}  ", {
	i: id,
	$entry:  "post"
      }).post[0];
}

function BlogDB.prototype.updatePost(id, title, body) {
  return this.dbURI.query("update posts "
			  + "set title = {t}, body = {b}, updated = NOW() "
			  + "where id = {i}", {
			    i: id,
			    t: title,
			    b: body
			  }).entry.@updateCount == 1;
}

function BlogDB.prototype.deletePost(id) {
  return this.dbURI.query("delete from posts where id = {i}", {
			    i: id
			  }).entry.@updateCount == 1;
}

function BlogDB.prototype.listPosts(limit) {
  return this.dbURI.query(
    "select *, (select count(*) from comments where post_id = p.id) as comments "
      + "from posts p "
      + "order by id desc "
      + "limit " + parseInt(limit), {
	$result: "posts",
	$entry:  "post"
      });
}

function BlogDB.prototype.addComment(post_id, body) {
  let res = this.dbURI.query("insert into comments (post_id, body, created, updated) "
			     + "values ({p}, {b}, NOW(), NOW())", {
			       p: post_id,
			       b: body
			     });
  return parseInt(res.entry.identity);
}

function BlogDB.prototype.getComment(post_id, id) {
  return this.dbURI.query("select * from comments "
			  + "where id = {i} and post_id = {p} ", {
			    p: post_id,
			    i: id,
			    $entry:  "comment"
			  }).comment[0];
}

function BlogDB.prototype.updateComment(post_id, id, body) {
  return this.dbURI.query("update comments "
			  + "set body = {b}, updated = NOW() "
			  + "where id = {i} and post_id = {p}", {
			    p: post_id,
			    i: id,
			    b: body
			  }).entry.@updateCount == 1;
}

function BlogDB.prototype.deleteComment(post_id, id) {
  return this.dbURI.query("delete from comments where id = {i} and post_id = {p}", {
			    p: post_id,
			    i: id
			  }).entry.@updateCount == 1;
}

function BlogDB.prototype.listComments(post_id, limit) {
  return this.dbURI.query("select * from comments "
			  + "where post_id = {p} "
			  + "order by id "
			  + "limit " + parseInt(limit), {
			    p: post_id,
			    $result: "comments",
			    $entry:  "comment"
			  });
}
