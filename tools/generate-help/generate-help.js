#!/usr/bin/env esxx-js

esxx.include('esxx/harmony.js');

const err = java.lang.System.err;
const out = java.lang.System.out;

XML.prettyPrinting   = false;
XML.ignoreWhitespace = false;

var db;
var wordIDs = {};

function main(prog, cmd, db_files) {
  if (arguments.length < 3) {
    err.println("Usage: " + prog
		+ " <clear-db|index-db|zip-db|query|add-mdc|add-berlios> <db-path> [<arg1> ...]");
    return 10;
  }

  db = new URI("jdbc:h2:" + db_files);

  let rc = 0;

  if (cmd == "clear-db") {
    db.query("drop table docs if exists");

    db.query("create table docs"
	     + "("
		 + "id	    identity not null,"
		 + "section varchar not null,"
		 + "title   varchar not null,"
		 + "text    varbinary not null,"
		 + "uri	    varchar not null,"
		 + "primary key(id),"
		 + "unique (section, title),"
		 + "unique (uri)"
	     + ")");
  }
  else if (cmd == "index-db") {
    rc = recreate_index();
  }
  else if (cmd == "zip-db") {
    db.query("backup to 'esxx-help.zip'");
  }
  else if (cmd == "query") {
    rc = query(Array.splice(arguments, 3));
  }
  else if (cmd == "dump-mdc") {
    rc = dump_book("https://developer.mozilla.org/en/Core_JavaScript_1.5_Reference", dump_mdc);
  }
  else {
    err.println("Unknown command: " + cmd);
    return 10;
  }

  return rc;
}

function dump_book(base, dump_page) {
  let visited	 = {};
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


function recreate_index() {
  db.query("drop table words if exists");
  db.query("drop table doc_words if exists");

  db.query("create table words"
	     + "("
		 + "id	 identity not null,"
		 + "word varchar not null,"
		 + "primary key(id),"
		 + "unique (word)"
	     + ")");

  db.query("create table doc_words"
	     +"("
		+ "word_id integer not null,"
		+ "doc_id  integer not null,"
		+ "title boolean not null,"
		+ "foreign key (word_id) references words(id) on delete cascade,"
		+ "foreign key (doc_id)	 references docs(id) on delete cascade"
	     + ")");

  for each (let entry in db.query("select id, section, title from docs").entry) {
    create_index(parseInt(entry.id), entry.section.toString(), entry.title.toString());
  }

  return 0;
}

function query(terms) {
  let final_docs;

  for each (let term in terms) {
    term = term.toLowerCase();

    let docs = db.query("select distinct dw.doc_id "
			+ "from words w "
			+ "inner join doc_words dw on dw.word_id = w.id "
			+ "where w.word = {0} "
			+ "order by doc_id",
			[term]);

    docs = [parseInt(id) for each (id in docs.entry.doc_id)];

    if (!final_docs) {
      final_docs = docs;
    }
    else {
      final_docs = docs.filter(function (v) { return final_docs.indexOf(v) != -1; });
    }
  }

  if (final_docs.length == 0) {
    out.println("No documents matched the given terms.");
    return 5;
  }
  else if (final_docs.length > 1) {
    out.println("The following documents matched the given terms. Please be more specific.");

    let matches = db.query("select section, title from docs where id in ({docs})", {
      docs: final_docs
    });

    for each (let match in matches.entry) {
      out.println(match.section + "." + match.title);
    }

    return 5;
  }
  else {
    out.println(db.query("select utf8tostring(expand(text)) as text from docs where id = {0}",
			 final_docs).entry.text);
    return 0;
  }
}


// Helper functions

function dump_mdc(url) {
  err.println("Dumping " + url);

  let page = new URI(url).load("text/html");

  let breadcrumb = page..div.(@["class"] == "hierarchy").ol.li.a.text();

  let sect  = breadcrumb[breadcrumb.length() - 2].toString();
  let title = page..h1.(@id == "title").toString();
//  let toc   = page..div.(@id == "pageToc");
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

    db.query("insert into docs (section, title, text, uri) "
	     + "values ({0}, {1}, compress(stringtoutf8({2}), 'deflate'), {3})",
	     [section, title, text, url]);
  }
  finally {
    new URI("mdc.tmp.html").remove();
    new URI("mdc.tmp.txt").remove();
  }
}

function create_index(id, section, title) {
  let delim = /[^-.a-zA-Z0-9]|\.+/; // Split on non-word chars or '...', but keep '1.5'
  let path  = section + "." + title;
  let words = path.split(delim);

  words.push(path);
  words.push(section);
  words.push(title);

  add_words(id, words, true);
}

function add_words(id, words, is_title) {
  for each (let word in words) {
    word = word.toLowerCase().trim();

    if (word !== "") {
      if (!wordIDs.hasOwnProperty(word)) {
	wordIDs[word] = parseInt(db.query("insert into words (word) values ({0})",
					  [word]).entry.identity);
      }

      db.query("insert into doc_words (word_id, doc_id, title) "
	       + "values ({0}, {1}, {2})",
	       [wordIDs[word], id, is_title]);
    }
  }
}
