#!/usr/bin/env esxx-js

esxx.include("esxx/Test.js");
esxx.include("../BlogDB.js");

var db = new BlogDB("jdbc:h2:mem:BlogDB-test");

function testDB() {
  try {
    Assert.fnThrows(function () { db.listPosts(10); });
    Assert.fnThrows(function () { db.listComments(0, 10); });

    Assert.isFalse(db.checkDB());

    db.createDB();

    Assert.isTrue(db.checkDB());

    Assert.fnNotThrows(function () { db.listPosts(10); });
    Assert.fnNotThrows(function () { db.listComments(0, 10); });

    Assert.areIdentical(db.listPosts(10).post.length(), 0);
    Assert.areIdentical(db.listComments(0, 10).comment.length(), 0);

    db.destroyDB();

    Assert.fnThrows(function () { db.listPosts(10); });
    Assert.fnThrows(function () { db.listComments(0, 10); });
  }
  finally {
    db.destroyDB();
  }
}

function testPosts() {
  db.createDB();

  try {
    // addPost
    let one = db.addPost("First post", "ESXX rocks");
    let two = db.addPost("Second post", "Cool stuff");

    Assert.areIdentical(one, 1);
    Assert.areIdentical(two, 2);

    // listPosts
    Assert.areIdentical(db.listPosts(10).post.length(), 2);
    Assert.areIdentical(db.listPosts(1).post.length(), 1);

    Assert.areEqual(db.listPosts(10).post[0].title, "Second post");
    Assert.areEqual(db.listPosts(10).post[1].title, "First post");

    // getPost
    Assert.areEqual(db.getPost(one).title, "First post");
    Assert.areEqual(db.getPost(one).body, "ESXX rocks");

    Assert.areEqual(db.getPost(two).title, "Second post");
    Assert.areEqual(db.getPost(two).body, "Cool stuff");

    // updatePost
    Assert.isTrue(db.updatePost(one, "Initial post", "ESXX owns"));
    Assert.isFalse(db.updatePost(99999, "Unknown", "post"));

    Assert.areEqual(db.getPost(one).title, "Initial post");
    Assert.areEqual(db.getPost(one).body, "ESXX owns");

    Assert.areEqual(db.getPost(two).title, "Second post");
    Assert.areEqual(db.getPost(two).body, "Cool stuff");


    // deletePost
    Assert.isTrue(db.deletePost(one));
    Assert.isFalse(db.deletePost(99999));

    Assert.areIdentical(db.listPosts(10).post.length(), 1);
    Assert.isUndefined(db.getPost(one));
    Assert.areEqual(db.listPosts(10).post[0].title, "Second post");

  }
  finally {
    db.destroyDB();
  }
}

function testComments() {
  db.createDB();

  try {
    let p1 = db.addPost("", "");
    let p2 = db.addPost("", "");
    let p3 = db.addPost("", "");

    // addComment
    let c11 = db.addComment(p1, "Comment 1.1");
    let c12 = db.addComment(p1, "Comment 1.2");

    let c21 = db.addComment(p2, "Comment 2.1");
    let c22 = db.addComment(p2, "Comment 2.2");
    let c23 = db.addComment(p2, "Comment 2.3");

    Assert.areIdentical(c11, 1);
    Assert.areIdentical(c23, 5);

    // Comment count
    Assert.areEqual(db.getPost(p1).comments, 2);
    Assert.areEqual(db.getPost(p2).comments, 3);
    Assert.areEqual(db.getPost(p3).comments, 0);
    Assert.areEqual(db.listPosts(10).post[0].comments, 0);
    Assert.areEqual(db.listPosts(10).post[1].comments, 3);
    Assert.areEqual(db.listPosts(10).post[2].comments, 2);


    // listComments
    Assert.areIdentical(db.listComments(p1, 10).comment.length(), 2);
    Assert.areIdentical(db.listComments(p2, 10).comment.length(), 3);
    Assert.areIdentical(db.listComments(p3, 10).comment.length(), 0);

    Assert.areIdentical(db.listComments(p2, 1).comment.length(), 1);

    Assert.areEqual(db.listComments(p1, 10).comment[0].body, "Comment 1.1");
    Assert.areEqual(db.listComments(p1, 10).comment[1].body, "Comment 1.2");

    Assert.areEqual(db.listComments(p2, 10).comment[0].body, "Comment 2.1");
    Assert.areEqual(db.listComments(p2, 10).comment[1].body, "Comment 2.2");
    Assert.areEqual(db.listComments(p2, 10).comment[2].body, "Comment 2.3");

    // getComment
    Assert.areEqual(db.getComment(p1, c12).body, "Comment 1.2");
    Assert.areEqual(db.getComment(p2, c21).body, "Comment 2.1");
    Assert.isUndefined(db.getComment(p1, c21));

    // updateComment
    Assert.isTrue(db.updateComment(p1, c11, "Comment 1a"));
    Assert.isTrue(db.updateComment(p2, c23, "Comment 2c"));

    Assert.isFalse(db.updateComment(p1, c23, "Comment 2c"));

    Assert.areEqual(db.listComments(p1, 10).comment[0].body, "Comment 1a");
    Assert.areEqual(db.listComments(p2, 10).comment[2].body, "Comment 2c");

    // deleteComment
    Assert.isTrue(db.deleteComment(p1, c11));
    Assert.isTrue(db.deleteComment(p2, c22));

    Assert.isFalse(db.deleteComment(p1, c23));

    Assert.areIdentical(db.listComments(p1, 10).comment.length(), 1);
    Assert.areIdentical(db.listComments(p2, 10).comment.length(), 2);
    Assert.areIdentical(db.listComments(p3, 10).comment.length(), 0);

    Assert.areEqual(db.listComments(p1, 10).comment[0].body, "Comment 1.2");
    Assert.areEqual(db.listComments(p2, 10).comment[1].body, "Comment 2c");

    // Cascading delete
    Assert.isTrue(db.deletePost(p2));

    Assert.areIdentical(db.listComments(p1, 10).comment.length(), 1);
    Assert.areIdentical(db.listComments(p2, 10).comment.length(), 0);
    Assert.areIdentical(db.listComments(p3, 10).comment.length(), 0);
  }
  finally {
    db.destroyDB();
  }
}
