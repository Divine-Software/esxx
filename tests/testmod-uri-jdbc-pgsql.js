
/* For this testcase to work, you must first execute the following
 * PostgreSQL commands:
 *
 * $ createuser --no-createdb --no-createrole --no-superuser esxx_test
 * (set password to 'esxx_test')
 * $ createdb -O esxx_test esxx_test
 *
 */

testRunner.add(new TestCase({
  name: "testmod-uri-jdbc-pgsql",

  init: function() {
    java.lang.Class.forName("org.postgresql.Driver");

    this.db = new URI("jdbc:postgresql:esxx_test");
    this.db.params = [{ name: "user",              value: "esxx_test" },
		      { name: "password",          value: "esxx_test" },
		     ];

    // Test server connectivity
    this.db.query("select 0");
  },

  setUp: function() {
    this.db.query("drop table if exists test");
    this.db.query("create table test (id serial primary key, " +
				      "string varchar(20), number int)");
  },

  tearDown: function() {
    this.db.query("drop table test");
  },

  testQueryInsertJS: function() {
    let one = this.db.query("insert into test (string, number) values ({0}, {1});" +
			    "select last_value FROM test_id_seq",
			    ["one", 1]);
    let two = this.db.query("insert into test values "
			    + "(default, {s1}, {n1}), (default, {s2}, {n2});" +
			    "select last_value FROM test_id_seq",
			    { s1: "two", n1: 2,
			      s2: "three", n2: 3,
			      $result: "res", $entry: "ent" });

    Assert.that(one.length() == 2, "INSERT/SELECT did not generate one two result sets")
    Assert.that(one.entry.length() == 1, "INSERT/SELECT did not generate one single entry")
    Assert.that(one..last_value.length() == 1, "INSERT/SELECT did not generate one single LAST_VALUE")

    Assert.areEqual(one.@updateCount, 1, "updateCount is not 1");
    Assert.areEqual(one.entry.last_value, 1, "LAST_VALUE of first INSERT/SELECT was not 1");

    Assert.areEqual(two.@updateCount, 2, "updateCount is not 2");
    Assert.areEqual(two.ent.last_value, 3, "LAST_VALUE of second INSERT/SELECT was not 3");
    Assert.areEqual(two[0].localName(), "res", "result element #1 name is not 'res'");
    Assert.areEqual(two[1].localName(), "res", "result element #2 name is not 'res'");
  },

  testQueryInsertXML: function() {
    let one = this.db.query("insert into test (string, number) values ({0}, {1});" +
			    "select last_value FROM test_id_seq",
			    <><e>one</e><e>1</e></>);
    let two = this.db.query("insert into test values "
			    + "(default, {s1}, {n1}), (default, {s2}, {n2});" +
			    "select last_value FROM test_id_seq",
			    <elem>
			    <s1>two</s1>   <n1>2</n1>
			    <s2>three</s2> <n2>3</n2>
			    </elem>
			    );

    Assert.that(one.length() == 2, "INSERT/SELECT did not generate one two result sets")
    Assert.that(one.entry.length() == 1, "INSERT/SELECT did not generate one single entry")
    Assert.that(one..last_value.length() == 1, "INSERT/SELECT did not generate one single LAST_VALUE")

    Assert.areEqual(one.@updateCount, 1, "updateCount is not 1");
    Assert.areEqual(one.entry.last_value, 1, "LAST_VALUE of first INSERT/SELECT was not 1");

    Assert.areEqual(two.@updateCount, 2, "updateCount is not 2");
    Assert.areEqual(two.entry.last_value, 3, "LAST_VALUE of second INSERT/SELECT was not 3");
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

//  testQueryBatch: function() {
//    let res = this.db.query("insert into test (string, number) values ({0}, {1})",
//			    { 0:"one", 1: 1}, ["two", 2],
//			    <><e>three</e><e>3</e></>);
//    esxx.log.debug(res);
//  },

  testTransaction: function() {
    let db = this.db;

    Assert.fnThrows(function() {
      db.query(function() {
	db.query("insert into test values (default, 'one', 1)");
	throw "Transaction rolled back";
      });
    }, "string", "Transaction did not throw a string");

    Assert.areEqual(db.query("select count(*) as cnt from test").entry.cnt, 0,
		    "Transaction #1 did not roll back");

    db.query(function() {
      db.query("insert into test values (default, 'one', 1)");
      db.query("insert into test values (default, 'two', 2)");
    });

    Assert.areEqual(db.query("select count(*) as cnt from test").entry.cnt, 2,
		    "Transaction #2 did not insert two row");
  },

  testMetaData: function() {
    this.db.query("insert into test values (default, 'one', 1)");

    let res = this.db.query("select * from test", { $meta: "meta" });

    Assert.areEqual([t.toString() for each (t in res.meta.*.@type)].join(','), 
		    "integer,varchar,integer", "Query returned incorrect types");
  }

}));
