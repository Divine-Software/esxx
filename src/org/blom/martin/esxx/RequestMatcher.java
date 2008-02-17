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
import java.util.Map;
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
      if (method.isEmpty()) {
	method = "[^ ]+";
      }

      if (uri.isEmpty()) {
	uri = ".*";
      }

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
    }


    public Match matchRequest(String method, String uri,
			      Context cx, Scriptable scope) {
      Matcher m = compiledPattern.matcher(method + ":" + uri);

      if (m.matches()) {
	int group = 1;
	
	for (Request r : patterns) {
	  if (m.start(group) != -1) {
	    Match       res = new Match();

	    // We found the request that matched. Now calculate return
	    // values and exit.

	    res.params = cx.newObject(scope);

//	    // Put unnamed groups, incl. a faked group 0
//	    ScriptableObject.putProperty(res.params, 0, uri);
	    for (int i = 0; i <= r.numGroups; ++i) {
	      ScriptableObject.putProperty(res.params, i, m.group(group + i));
	    }

	    // Put named groups
	    for (Map.Entry<String,Integer> e : r.namedGroups.entrySet()) {
	      String  name = e.getKey();
	      String value = m.group(group + e.getValue());

	      ScriptableObject.putProperty(res.params, name, value);
	    }
	    
	    StringBuffer sb = new StringBuffer();
	    Matcher      tm = Request.namedReferencePattern.matcher(r.handlerTemplate);

	    while (tm.find()) {
	      String    ref = tm.group();
	      String   name = ref.substring(1, ref.length() - 1);
	      int group_num = group + r.namedGroups.get(name);

	      tm.appendReplacement(sb, m.group(group_num));
	    }
	    tm.appendTail(sb);

	    res.handler = sb.toString();

	    return res;
	  }

	  group += r.numGroups + 1;
	}

	throw new InternalError("Internal error in matchRequest: Failed to find the match!");
      }
      else {
	return null;
      }
    }


    public static class Match {
	String handler;
	Scriptable params;

	public String toString() {
	  StringBuilder sb = new StringBuilder();

	  sb.append("RequestMatcher.Match[");
	  sb.append(handler);
	  
	  for (Object o : params.getIds()) {
	    sb.append(", ");
	    sb.append(o);
	    sb.append(": ");
	    if (o instanceof Integer) {
	      sb.append(params.get((Integer) o, params));
	    }
	    else {
	      sb.append(params.get((String) o, params));
	    }
	  }

	  sb.append("] ");

	  return sb.toString();
	}
    }


    private static class Request {
	public Request(String m, String u, String h) {
	  handlerTemplate = h;
	  namedGroups = new TreeMap<String, Integer>();
	  compilePattern(m, u);
	}

	private void compilePattern(String method, String uri) {
	  String regex = "(?:" + method + "):(?:" + uri + ")";
	  Matcher   gm = groupPattern.matcher(regex);
	  Matcher   nm = namedGroupPattern.matcher(regex);


	  TreeMap<Integer, Integer> offset2group = new TreeMap<Integer, Integer>();
	  numGroups = 0;

// 	  System.err.println(regex);

	  while (gm.find()) {
	    ++numGroups;
	    offset2group.put(gm.start(1), numGroups);
// 	    System.err.println(gm.group(1) + " at " + gm.start(1));
	  }

	  while (nm.find()) {
	    Integer g = offset2group.get(nm.start(1));

// 	    System.err.println(nm.group(1) + " at " + nm.start(1));

	    if (g == null) {
	      throw new InternalError("Internal error #1 in RequestMatcher.compilePattern()");
	    }

	    String match = nm.group(1);
	    String name = match.substring(3, match.length() - 1);

	    namedGroups.put(name, g);
	  }

	  String unnamed = nm.replaceAll("(");

	  // Compile regex and make sure our group count is the same as Java's.
//	  try {
	    pattern = Pattern.compile(unnamed);
// 	  }
// 	  catch (PatternSyntaxException ex) {
// 	    throw new ESXXException("Unable to compile request regex: " + unnamed);
// 	  }

	  if (pattern.matcher("").groupCount() != numGroups) {
// 	    System.err.println(pattern.toString());
// 	    System.err.println(pattern.matcher("").groupCount());
// 	    System.err.println(numGroups);
	    throw new InternalError("Internal error #2 in RequestMatcher.compilePattern()");
	  }
	}

	private String handlerTemplate;
	private TreeMap<String, Integer> namedGroups;
	private int numGroups;
	private Pattern pattern;

	/** A Pattern that matches '(' or '(?<' but not '\(' or '(?' */
	private static Pattern groupPattern = Pattern.compile("(((^\\()|((?<!\\\\)\\())" +
							      "(?=[^?]|\\?<))");

	/** A Pattern that matches '(?<...>', where ... is anything but a '>' */
	private static Pattern namedGroupPattern = Pattern.compile("(\\(\\?<[^>]+>)");

	/** A Pattern that matches '{...}', where ... is anything but a '}' */
	static Pattern namedReferencePattern = Pattern.compile("(\\{[^\\}]+\\})");
    }

    private LinkedList<Request> patterns;
    private Pattern compiledPattern;


    public static void main(String args[]) {
      RequestMatcher rm = new RequestMatcher();
      
      rm.addRequestPattern("GET", 
			   "articles", 
			   "1");
      rm.addRequestPattern("(GET|POST)", 
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
			   "{method}_{cat}_{item}_{id}");

      rm.compile();

      Context cx = Context.enter();
      Scriptable scope = new ImporterTopLevel(cx);

      System.out.println(rm.matchRequest("POST", "articles/books", cx, scope));
      System.out.println(rm.matchRequest("POST", "articles/", cx, scope));
      System.out.println(rm.matchRequest("GET", "articles/books", cx, scope));
      System.out.println(rm.matchRequest("GET", "articles/", cx, scope));
      System.out.println(rm.matchRequest("REPORT", "article(s)/100/hejhopp/12", cx, scope));
      System.out.println(rm.matchRequest("DELETE", "gröna/äpplen/10", cx, scope));
    }
}
