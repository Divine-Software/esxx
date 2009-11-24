#!/usr/bin/env esxx-js

const err = java.lang.System.err;
const out = java.lang.System.out;

XML.prettyPrinting   = false;
XML.ignoreWhitespace = false;

var db;
var wordIDs = {};

function main(prog, db_files) {
  if (arguments.length != 2) {
    err.println("Usage: " + prog + " <db-path>");
    return 10;
  }

  db = new URI("jdbc:h2:" + db_files);

  db.query("drop table docs if exists");
  try { db.query("drop table words"); } catch (ignored) {}
  try { db.query("drop table doc_words"); } catch (ignored) {}

  db.query("create table docs"
	   + "("
	       + "id      identity not null,"
	       + "section varchar not null,"
	       + "title   varchar not null,"
	       + "text    varbinary not null,"
	       + "uri     varchar not null,"
	       + "primary key(id),"
	       + "unique (section, title),"
	       + "unique (uri)"
	   + ")");

  db.query("create table words"
	   + "("
	       + "id   identity not null,"
	       + "word varchar not null,"
	       + "primary key(id),"
	       + "unique (word)"
	   + ")");

  db.query("create table doc_words"
	   +"("
	      + "word_id integer not null,"
	      + "doc_id  integer not null,"
	      + "section boolean not null,"
	      + "foreign key (word_id) references words(id) on delete cascade,"
	      + "foreign key (doc_id)  references docs(id) on delete cascade"
	   + ")");

  dump_book("https://developer.mozilla.org/en/Core_JavaScript_1.5_Reference", dump_mdc);

  return 0;
}

function dump_book(base, dump_page) {
  let visited    = {};
  let candidates = [base];

  while (candidates.length > 0) {
    let link = candidates.shift();

    link = link.replace(/#.*/, ""); // No fragments

    if (!visited[link] && link.indexOf(base) == 0) {
      visited[link] = true;

      let links = dump_page(link, db);

      candidates = candidates.concat(links);
    }
  }

  return 0;
}

function dump_mdc(url) {
  err.println("Dumping " + url);

  let page = new URI(url).load("text/html");

  let breadcrumb = page..div.(@["class"] == "hierarchy").ol.li.a.text();

  let sect  = breadcrumb[breadcrumb.length() - 2].toString();
  let title = page..h1.(@id == "title").toString();
  let toc   = page..div.(@id == "pageToc");
  let text  = page..div.(@id == "pageText");

  if (breadcrumb.length() > 2 /* Prevent redirects to MDC / Mozilla Developer Center */
      && page..a.(@["class"] == "pageMoved").length() == 0
      && sect && title && toc.*.length() > 0 && text.*.length() > 0) {
    import_html(url, sect, title,
		<html>
		  <head>
		    <title>{title}</title>
		  </head>
		  <body>
		    {toc}
		    {text}
	 	  </body>
		</html>);
  }

  return [a.toString() for each (a in text..a.@href)];
}

function import_html(url, section, title, html) {
  err.println("Importing " + section + " / " + title);

  try {
    new URI("mdc.tmp.html").save(html);

    let p = java.lang.Runtime.getRuntime().exec(["/bin/sh", 
						 "-c",
						 "links -assume-codepage utf8 -dump "
						 + "mdc.tmp.html > mdc.tmp.txt"]);
    if (p.waitFor() != 0) {
      throw "Failed to convert " + url + " to plain text.";
    }

    let text = new URI("mdc.tmp.txt").load("text/plain");

    let doc_id = db.query("insert into docs (section, title, text, uri) "
			  + "values ({0}, {1}, compress(stringtoutf8({2}), 'deflate'), {3})", 
			  [section, title, text, url]).entry.identity;
    doc_id = parseInt(doc_id);

    add_words(doc_id, section, true);
    add_words(doc_id, title, false);
  }
  finally {
    new URI("mdc.tmp.html").remove();
    new URI("mdc.tmp.txt").remove();
  }

}

function add_words(id, str, is_section) {
  let words = str.split(/[^a-zA-Z0-9.-]+/);

  for each (let word in words) {
    word = word.toLowerCase();

    if (!wordIDs.hasOwnProperty(word)) {
      wordIDs[word] = parseInt(db.query("insert into words (word) values ({0})", 
					[word]).entry.identity);
    }

    db.query("insert into doc_words (word_id, doc_id, section) "
	     + "values ({0}, {1}, {2})", 
	     [wordIDs[word], id, is_section]);
  }
}

// select d.section, d.title
// from words w
// inner join doc_words dw on w.id = dw.word_id 
// inner join docs d on dw.doc_id = d.id
// where w.word = 'object'
