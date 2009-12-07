
/* For this testcase to work, you must first execute the following
 * MySQL commands:
 *
 * mysql> create database esxx_test;
 * mysql> grant usage on *.* to esxx_test@localhost identified by 'esxx_test';
 * mysql> grant all privileges on esxx_test.* to esxx_test@localhost;
 *
 */

testRunner.add(new TestCase({
  name: "testmod-uri-jdbc-mysql",

  setUp: function() {
    java.lang.Class.forName("com.mysql.jdbc.Driver").newInstance();

    this.db = new URI("jdbc:mysql://localhost/esxx_test");
    this.db.params = [{ name: "user",              value: "esxx_test" },
		      { name: "password",          value: "esxx_test" },
		      { name: "allowMultiQueries", value: "true"      }
		     ];

    this.db.query("drop table if exists test");
    this.db.query("create table test (id int auto_increment primary key, " +
				      "string varchar(20), number int)");
  },

  tearDown: function() {
    this.db.query("drop table test");
  },

  testQueryInsertJS: function() {
    let one = this.db.query("insert into test (string, number) values ({0}, {1})",
			    ["one", 1]);
    let two = this.db.query("insert into test values "
			    + "(default, {s1}, {n1}), (default, {s2}, {n2})",
			    { s1: "two", n1: 2,
			      s2: "three", n2: 3,
			      $result: "res", $entry: "ent", $updateCount: "uc" });

    Assert.that(one.entry.length() == 1, "INSERT did not generate one single entry")
    Assert.that(one..generated_key.length() == 1, "INSERT did not generate one single GENERATED_KEY")

    Assert.areEqual(one.@updateCount, 1, "updateCount is not 1");
    Assert.areEqual(one.entry.generated_key, 1, "GENERATED_KEY of first INSERT was not 1");

    Assert.areEqual(two.@uc, 2, "uc is not 2");
    Assert.areEqual(two.ent.generated_key[0], 2, "GENERATED_KEY of second INSERT was not 2");
    Assert.areEqual(two.ent.generated_key[1], 3, "GENERATED_KEY of second INSERT was not 3");
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
    Assert.that(one..generated_key.length() == 1, "INSERT did not generate one single GENERATED_KEY")

    Assert.areEqual(one.@updateCount, 1, "updateCount is not 1");
    Assert.areEqual(one.entry.generated_key, 1, "GENERATED_KEY of first INSERT was not 1");

    Assert.areEqual(two.@updateCount, 2, "updateCount is not 2");
    Assert.areEqual(two.entry.generated_key[0], 2, "GENERATED_KEY of second INSERT was not 2");
    Assert.areEqual(two.entry.generated_key[1], 3, "GENERATED_KEY of second INSERT was not 3");
  },

  testQuerySelect: function() {
    this.db.query("insert into test values (default, 'one', 1)");
    this.db.query("insert into test values (default, 'two', 2)");

    let one   = this.db.query("select number from test where string = {one}", { one: "one" });
    let two   = this.db.query("select string from test where id = {0}", [2]);
    let multi = this.db.query("select string from test where id = 1 ;" +
			      "select number from test");

    Assert.that(one.entry.length() == 1, "SELECT did not return exactly one entry");
    Assert.areEqual(one.entry.number, 1, "SELECT did not return 1");

    Assert.that(two.entry.length() == 1, "SELECT did not return exactly one entry");
    Assert.areEqual(two.entry.string, "two", "SELECT did not return 'two'");

    Assert.that(multi.entry.length() == 3, "Multi-SELECT did not return exactly three entries");
    Assert.areEqual(multi[0].entry.string, "one", "Multi-SELECT #1 did not return 'one'");
    Assert.areEqual(multi[1].entry[0].number, 1, "Multi-SELECT #2 did not return 1");
    Assert.areEqual(multi[1].entry[1].number, 2, "Multi-SELECT #2 did not return 2");
  },

  testQueryBatch: function() {
    let res = this.db.query("insert into test (string, number) values ({0}, {1})",
			    { 0:"one", 1: 1}, ["two", 2],
			    <><e>three</e><e>3</e></>);
//    esxx.log.debug(res);
  }

}));
