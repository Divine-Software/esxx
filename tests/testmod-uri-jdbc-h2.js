
testRunner.add(new TestCase({
  name: "testmod-uri-jdbc-h2",

  setUp: function() {
    this.db = new URI("jdbc:h2:mem:testmod-uri-jdbc");
    this.db.query("create table test (id identity, string varchar, number int)");
  },

  tearDown: function() {
    this.db.query("drop all objects delete files");
  },

  testQueryInsertJS: function() {
    let one = this.db.query("insert into test (string, number) values ({0}, {1})",
			    ["one", 1]);
    let two = this.db.query("insert into test values "
			    + "(default, {s1}, {n1}), (default, {s2}, {n2})",
			    { s1: "two", n1: 2,
			      s2: "three", n2: 3,
			      $result: "res", $entry: "ent" });

    Assert.that(one.entry.length() == 1, "INSERT did not generate one single entry")
    Assert.that(one..identity.length() == 1, "INSERT did not generate one single identity")

    Assert.areEqual(one.@updateCount, 1, "updateCount is not 1");
    Assert.areEqual(one.entry.identity, 1, "IDENTITY of first INSERT was not 1");

    Assert.areEqual(two.@updateCount, 2, "updateCount is not 2");
    Assert.areEqual(two.ent.identity, 3, "IDENTITY of second INSERT was not 3");
    Assert.areEqual(two.localName(), "res", "result element name is not 'res'");
  },

  testQueryInsertXML: function() {
    let one = this.db.query("insert into test (string, number) values ({0}, {1})",
			    <><e>one</e><e>1</e></>);
    let two = this.db.query("insert into test values "
			    + "(default, {s1}, {n1}), (default, {s2}, {n2})",
			    <elem>
			    <s1>two</s1>   <n1>2</n1>
			    <s2>three</s2> <n2>3</n2>
			    </elem>
			    );

    Assert.that(one.entry.length() == 1, "INSERT did not generate one single entry")
    Assert.that(one..identity.length() == 1, "INSERT did not generate one single identity")

    Assert.areEqual(one.@updateCount, 1, "updateCount is not 1");
    Assert.areEqual(one.entry.identity, 1, "IDENTITY of first INSERT was not 1");

    Assert.areEqual(two.@updateCount, 2, "updateCount is not 2");
    Assert.areEqual(two.entry.identity, 3, "IDENTITY of second INSERT was not 3");
  },

  testQuerySelect: function() {
    this.db.query("insert into test values (default, 'one', 1)");
    this.db.query("insert into test values (default, 'two', 2)");

    let one   = this.db.query("select number from test where string = {one}", { one: "one" });
    let two   = this.db.query("select string from test where id = {0}", [2]);

    Assert.that(one.entry.length() == 1, "SELECT did not return exactly one entry");
    Assert.areEqual(one.entry.number, 1, "SELECT did not return 1");

    Assert.that(two.entry.length() == 1, "SELECT did not return exactly one entry");
    Assert.areEqual(two.entry.string, "two", "SELECT did not return 'two'");
  },

  testQueryBatch: function() {
    let res = this.db.query("insert into test (string, number) values ({0}, {1})",
			    { 0:"one", 1: 1}, ["two", 2],
			    <><e>three</e><e>3</e></>);
//    esxx.log.debug(res);
  }

}));
