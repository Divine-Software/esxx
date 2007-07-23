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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Collections;
import java.util.HashMap;

public class RequestMatcher {
    public RequestMatcher() {
      partHandlers = new ArrayList<PartHandlers>();
    }

    public void addHandler(String method, String path, String function) {
      Handler handler = new Handler(method, path, function);

      while (partHandlers.size() < handler.pathParts.length) {
	partHandlers.add(new PartHandlers());
      }

      for (int i = 0; i < handler.pathParts.length; ++i) {
	String       part = handler.pathParts[i];
	PartHandlers part_handlers = partHandlers.get(i);

	part_handlers.addHandler(handler, part);
      }
    }

    public String getHandler(String method, String path) {
      PartHandlers part_handlers = partHandlers.get(0);

      List set = union(part_handlers.anyHandlers, 
		       part_handlers.valueHandlers.get(method));
      
      String[] path_parts = path.split("/");

      for (int i = 0; i < path_parts.length; ++i) {
	part_handlers = partHandlers.get(i + 1);

	List intersection_set = union(part_handlers.anyHandlers,
				      part_handlers.valueHandlers.get(path_parts[i]));

	intersect(set, intersection_set);
      }

      return null;
    }

    private List<Handler> union(List<Handler> l1, List<Handler> l2) {
      LinkedList 
      return l1;
    }

    private void intersect(List l1, List l2) {
    }

    private static String makeKey(int part, String value) {
      return part + "/" + value;
    }

    private static class Handler
      implements Comparable<Handler> {
	public Handler(String method, String path, String function) {
	  compareKey      = method + "/" + path;
	  handlerFunction = function;

	  if (method.equals("?")) {
	    method = "";
	  }

	  String[] path_parts = path.split("/");

	  pathParts = new String[path_parts.length + 1];

	  // Install method as part 0
	  pathParts[0] = method;
	  
	  // Install path parts as remaining parts (turning "?" into
	  // "" as well)
	  for (int i = 0; i < path_parts.length; ++i) {
	    if (path_parts[i].equals("?")) {
	      pathParts[i + 1] = "";
	    }
	    else {
	      pathParts[i + 1] = path_parts[i];
	    }
	  }
	}

	public int compareTo(Handler h) {
	  return compareKey.compareTo(h.compareKey);
	}

	public String compareKey;
	public String handlerFunction;
	public String[] pathParts;
    }

    private static class PartHandlers {
	public PartHandlers() {
	  anyHandlers   = new ArrayList<Handler>();
	  valueHandlers = new HashMap<String, ArrayList<Handler>>();
	}

	public void addHandler(Handler handler, String part) {
	  ArrayList<Handler> list;

	  if (part.isEmpty()) {
	    // Add to wildcard list
	    list = anyHandlers;
	  }
	  else {
	    list = valueHandlers.get(part);
	  
	    if (list == null) {
	      list = new ArrayList<Handler>();
	      valueHandlers.put(part, list);
	    }
	  }
	
	  list.add(handler);

	  // We sort the list every time, which is wasteful, but since
	  // ESXX only adds handlers on application startup, it doesn't
	  // really matter.
	  Collections.sort(list);
	}

	public ArrayList<Handler> anyHandlers;
	public HashMap<String, ArrayList<Handler>> valueHandlers;
    }

    public ArrayList<PartHandlers> partHandlers;
}
