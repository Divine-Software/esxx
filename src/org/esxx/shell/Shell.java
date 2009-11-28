
package org.esxx.shell;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.URI;
import java.util.*;
import jline.*;
import org.esxx.*;
import org.esxx.util.*;
import org.mozilla.javascript.*;
import java.sql.SQLException;

public class Shell
  implements Runnable {

  public Shell(Context cx, Application app) {
    this.cx  = cx;
    this.app = app;
  }

  public void run() {
    try {
      final ConsoleReader console = new ConsoleReader();
      final StringBuilder sb     = new StringBuilder();
    
      console.addCompletor(new Completor() {
	  @SuppressWarnings("unchecked")
	  public int complete(String buffer, int cursor, List candidates) {
	    if (buffer.matches("\\s*\\\\h\\s.*" /* Help command */)) {
	      return helpCompletor.complete(buffer, cursor, candidates);
	    }
	    else {
	      return propCompletor.complete(buffer, cursor, candidates);
	    }
	  }

	  private Completor helpCompletor = new HelpCompletor(Shell.this);
	  private Completor propCompletor = new PropertyCompletor(app.getJSGlobal());
	});

      console.setAutoprintThreshhold(150);
      console.setUsePagination(true);

      System.out.println("Welcome to the ESXX Shell!");
      System.out.println("Enter JavaScript statements at the prompt. Tab completion is supported.");
      System.out.println("Use Escape to cancel the current statement and Control-D \\q to quit.");

      console.addTriggeredAction((char) 27, new ActionListener() {
	  public void actionPerformed(ActionEvent e) {
	    // Clear current command and exit from readLine
	    sb.setLength(0);
	    console.exitReadLine(true);
	  }
	});

      int line_counter = 1;
      boolean quit = false;

      while (!quit) {
	String prompt = line_counter == 1 ? "esxx> " : (line_counter + "> ");
	String line   = console.readLine(prompt);

	if (line == null) {
	  console.printNewline();
	  break;
	}

	sb.append(line);
	sb.append('\n');
	++line_counter;

	String statement = sb.toString().trim();

	if (statement.length() == 0) {
	  line_counter = 1;
	}
	else if (statement.charAt(0) == '\\') {
	  char cmd = statement.length() >= 2 ? statement.charAt(1) : '\0';

	  switch (cmd) {
	  case 'h':
	    displayHelp(console, statement.substring(2));
	    break;
	
	  case 'q':
	    quit = true;
	    break;

	  default:
	    System.out.println("Unknown command");
	    console.beep();
	    break;
	  }

	  sb.setLength(0);
	  line_counter = 1;
	}
	else if (cx.stringIsCompilableUnit(statement)) {
	  evaluateString(cx, app, statement);
	  sb.setLength(0);
	  line_counter = 1;
	}
      }
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private void evaluateString(Context cx, Application app, String statement) {
    Object result;
    Scriptable scope = app.getJSGlobal();

    try {
      result = cx.evaluateString(scope,
				 statement,
				 "ESXX Shell", 1,
				 null);
    }
    catch (Exception ex) {
      result = ex;
    }

    JS.printObject(cx, scope, result);
  }

  private synchronized void displayHelp(ConsoleReader console, String args) 
    throws IOException, SQLException {

    QueryCache help = getHelpQuery();
    URI    help_uri = getHelpURI();

    String[] terms = args.split(" ");

    ArrayList<Integer> final_docs = null;
    
    for (String term : terms) {
      if (!term.isEmpty()) {
	term = term.toLowerCase();

	ArrayQueryHandler qh = new ArrayQueryHandler(new String[] { term });

	help.executeQuery(help_uri, null,
			  "select distinct dw.doc_id"
			  + " from words w"
			  + " inner join doc_words dw on dw.word_id = w.id"
			  + " where w.word = {0}"
			  + " order by doc_id",
			  qh);

	ArrayList<Integer> docs = qh.<Integer>getColumn(0);

	if (final_docs == null) {
	  final_docs = docs;
	}
	else {
	  final_docs.retainAll(docs);
	}
      }
    }

    if (final_docs == null) {
      ArrayQueryHandler qh = new ArrayQueryHandler(null);

      help.executeQuery(help_uri, null,
			"select id from docs order by id",
			qh);

      final_docs = qh.<Integer>getColumn(0);
    }

    if (final_docs.size() == 0) {
      System.out.println("No documents matched the given terms.");
    }
    else if (final_docs.size() > 1) {
      ArrayQueryHandler qh = new ArrayQueryHandler(new Object[] { final_docs });

      help.executeQuery(help_uri, null,
			"select concat(section, '.', title) as name"
			+ " from docs"
			+ " where id in ({0})"
			+ " order by name",
			qh);
      
      System.out.println("The following documents matched the given terms."
			 + " Please be more specific.");
      console.printColumns(qh.<String>getColumn(0));
    }
    else {
      ArrayQueryHandler qh = new ArrayQueryHandler(new Object[] { final_docs.get(0) });

      help.executeQuery(help_uri, null,
			"select utf8tostring(expand(text))"
			+ " from docs"
			+ " where id = {0}",
			qh);
      console.printString((String) qh.getResult().get(0)[0]);
      console.printNewline();
    }
  }

  synchronized QueryCache getHelpQuery() {
    if (helpQuery == null) {
      helpQuery = new QueryCache(1, 60000, 10, 60000);
    }

    return helpQuery;
  }

  synchronized URI getHelpURI() 
    throws IOException {
    if (helpDB == null) {
      ESXX esxx = ESXX.getInstance();

      helpDB = File.createTempFile(getClass().getName(), ".zip");
      helpDB.deleteOnExit();

      IO.copyStream(esxx.openCachedURI(URI.create("esxx-rsrc:esxx-help.zip")), 
		    new FileOutputStream(helpDB));

      helpURI = URI.create("jdbc:h2:zip:" + helpDB + "!/api;DB_CLOSE_DELAY=-1");
    }

    return helpURI;
  }

  private static File helpDB;
  private static QueryCache helpQuery;
  private static URI helpURI;

  private Context cx;
  private Application app;
}