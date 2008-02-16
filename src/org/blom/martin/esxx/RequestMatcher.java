/*
     ESXX - The friendly ECMAscript/XML Application Server
     Copyright (C) 2007 Martin Blom <martin@blom.org>
     
     This program is free software; you can redistribute it and/or
     modify it under the terms of the GNU General Public License
     as published by the Free Software Foundation; either version 2
     of the License, or (at your option) any later version.
     
     This program is distributed in the hope that it will be useful,
     but WITHOUT ANY WARRANTY; without even the implied warranty of
     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
     GNU General Public License for more details.
     
     You should have received a copy of the GNU General Public License
     along with this program; if not, write to the Free Software
     Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
*/

package org.blom.martin.esxx;

import java.util.LinkedList;
import java.util.TreeMap;
import java.util.regex.*;
import org.mozilla.javascript.*;

public class RequestMatcher {
    public RequestMatcher() {
      patterns = new LinkedList<Request>();
    }

    public void clear() {
      patterns.clear();
    }

    public void addRequestPattern(String method, String uri, String handler) {
      patterns.add(new Request(method, uri, handler));
    }

    public void compile() {
      StringBuilder regex = new StringBuilder();

      for (Request r : patterns) {
	if (regex.length() > 0) {
 	  regex.append("|");
	}

	regex.append("^(");
 	regex.append(r.pattern.toString());
 	regex.append(")");
      }

      compiledPattern = Pattern.compile(regex.toString());
      System.out.println("compiledPattern: " + compiledPattern);
    }

    public Match matchRequest(String method, String uri,
			       Context cx, Scriptable scope) {
      Matcher m = compiledPattern.matcher(method + "\0" + uri);

      System.out.println("Checking " + method + " on " + uri);

      if (m.matches()) {
	int group = 1;
	
	for (Request r : patterns) {
	  if (m.start(group) != -1) {
	    System.out.println(r.handlerTemplate + " matches!");

	    for (String name : r.namedGroups.keySet()) {
	      System.out.println(name + " = " + m.group(group + r.namedGroups.get(name)));
	    }

	    Match res = new Match();

	    return res;
	  }

	  group += r.numGroups + 1;
	}
	throw new InternalError("Internal error in matchRequest: Failed to find match!");
      }
      else {
	return null;
      }
    }


    public static class Match {
	String handler;
	Scriptable params;
    }

    private static class Request {
	public Request(String m, String u, String h) {
	  handlerTemplate = h;
	  namedGroups = new TreeMap<String, Integer>();
	  compilePattern(m, u);
	}

	private void compilePattern(String method, String uri) {
	  String regex = "(?:" + method + ")\0(?:" + uri + ")";
//	  String regex = "(?:(?:" + method + ")/(?:" + uri + "))";
	  Matcher gm = groupPattern.matcher(regex);
	  Matcher nm = namedGroupPattern.matcher(regex);

//	  System.out.println("Processing pattern " + regex);

	  TreeMap<Integer, Integer> offset2group = new TreeMap<Integer, Integer>();
	  numGroups = 0;

	  while (gm.find()) {
	    ++numGroups;
	    offset2group.put(gm.start(1), numGroups);
//	    System.out.println("Group #" + numGroups + " at " + gm.start(1) + ": " + gm.group(1));
	  }

	  while (nm.find()) {
	    Integer g = offset2group.get(nm.start(1));

	    if (g == null) {
	      throw new InternalError("Internal error #1 in RequestMatcher.compilePattern()");
	    }

//	    System.out.println("Named #" + g + " at " + nm.start(1) + ": " + nm.group(1));

	    String match = nm.group(1);
	    String name = match.substring(3, match.length() - 1);

	    namedGroups.put(name, g);
	  }

	  String unnamed = nm.replaceAll("(");

	  System.out.println("Unnamed regex: " + unnamed + " (" + numGroups + " groups)");

	  // Compile regex and make sure our group count is the same as Java's.
	  pattern = Pattern.compile(unnamed);

	  if (pattern.matcher("").groupCount() != numGroups) {
	    throw new InternalError("Internal error #2 in RequestMatcher.compilePattern()");
	  }


// 	  for (String k : namedGroups.keySet()) {
// 	    System.out.println(k + ": " + namedGroups.get(k));
// 	  }
	}

	private String handlerTemplate;
	private TreeMap<String, Integer> namedGroups;
	private int numGroups;
	private Pattern pattern;

	/** A Pattern that matches '(' or '(?<' but not '\(' or '(?' */
	private static Pattern groupPattern = Pattern.compile("(((^\\()|((?<!\\\\)\\())" +
							      "([^?]|\\?<))");

	/** A Pattern that matches '(?<...>', where ... is anything but a '>' */
	private static Pattern namedGroupPattern = Pattern.compile("(\\(\\?<[^>]+>)");

	/** A Pattern that matches '{...}', where ... is anything but a '}' */
	private static Pattern namedReferencePattern = Pattern.compile("(\\{[^\\}]+\\})");
    }

    private LinkedList<Request> patterns;
    private Pattern compiledPattern;

    
    public static void main(String args[]) {
      RequestMatcher rm = new RequestMatcher();
      
      rm.addRequestPattern("GET", 
			   "articles", 
			   "1");
      rm.addRequestPattern("GET|POST", 
			   "articles/books", 
			   "2");
      rm.addRequestPattern("[^/]+", 
			   "articles/(?<article>[a-z]+)/(?<id>\\d+)", 
			   "3");
      rm.addRequestPattern("[^/]+", 
			   "article\\(s\\)/(\\d+)/(?<article>[a-z]+)/(?<id>\\d+)", 
			   "3");
      rm.addRequestPattern("(?<method>[^/]+)", 
			   "(?<cat>\\p{javaLetter}+)/(?<item>\\p{javaLetter}+)/(?<id>\\d+)",
			   "4");

      rm.compile();

      Context cx = null;
      Scriptable scope = null;

      rm.matchRequest("POST", "articles/books", cx, scope);
      rm.matchRequest("POST", "articles/", cx, scope);
      rm.matchRequest("GET", "articles/books", cx, scope);
      rm.matchRequest("GET", "articles/", cx, scope);
      rm.matchRequest("REPORT", "article(s)/100/hejhopp/12", cx, scope);
      rm.matchRequest("DELETE", "gröna/äpplen/10", cx, scope);
    }
}
