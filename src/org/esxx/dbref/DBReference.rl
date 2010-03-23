/*
     ESXX - The friendly ECMAscript/XML Application Server
     Copyright (C) 2007-2008 Martin Blom <martin@blom.org>

     This program is free software: you can redistribute it and/or
     modify it under the terms of the GNU General Public License
     as published by the Free Software Foundation, either version 3
     of the License, or (at your option) any later version.

     This program is distributed in the hope that it will be useful,
     but WITHOUT ANY WARRANTY; without even the implied warranty of
     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
     GNU General Public License for more details.

     You should have received a copy of the GNU General Public License
     along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.esxx.dbref;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Deque;
import java.util.LinkedList;
import java.util.TreeMap;

%%{
  machine dbpath;
  alphtype byte;

  prepush {
    if (top == stack.length) {
      int[] old = stack;
      stack = new int[old.length * 2];
      System.arraycopy(old, 0, stack, 0, old.length);
    }
  }

  action WordStart {
    wordOffset = 0;
  }

  action WordUnreserved {
    byte b = data[p - 1];

    if (b > (byte) 0xbf) {
      ++charOffset; // Advance char counter if start of UTF-8 sequence
    }

    appendWordByte(b);
  }

  action WordEncoded {
    appendWordByte((byte) encoded);
  }

  action EncodedStart {
    ++charOffset;
    encoded = 0;
  }

  action EncodedDigit {
    ++charOffset;
    encoded = encoded * 16 + data[p - 1] - '0';
  }

  action EncodedLowerHex {
    ++charOffset;
    encoded = encoded * 16 + data[p - 1] - 'a' + 10;
  }

  action EncodedUpperHex {
    ++charOffset;
    encoded = encoded * 16 + data[p - 1] - 'A' + 10;
  }

  action Table {
    table = getWord();
  }

  action Column {
    columns.add(getWord());
  }

  action Scope {
    String s = getWord();

    try {
      scope = Enum.valueOf(Scope.class, s.toUpperCase());
    }
    catch (IllegalArgumentException ignored) {
      charOffset -= s.length();
      fgoto *dbpath_error;
    }
  }

  action FilterCompStart {
    fcall filter_comp;
  }

  action FilterCompEnd {
    popFilter();
    fret;
  }

  action OptionalKey {
    tmpKey = getWord();
    paramRequired = false;
  }

  action RequiredKey {
    tmpKey = getWord();
    paramRequired = true;
  }

  action Value {
    tmpValue = getWord();
  }

  action Param {
    if (paramRequired) {
      requiredParams.put(tmpKey, tmpValue);
    }
    else {
      optionalParams.put(tmpKey, tmpValue);
    }
  }

  AMPERSAND     = '&';
  COMMA         = ',';
  EQUALS        = '=';
  EXCLAMATION   = '!';
  LPAREN        = '(';
  PERCENT       = '%';
  QUESTION      = '?';
  RPAREN        = ')';

  DIGIT         = digit % EncodedDigit;
  HEX_UPPER     = [A-F] % EncodedUpperHex;
  HEX_LOWER     = [a-f] % EncodedLowerHex;
  XDIGIT        = DIGIT | HEX_UPPER | HEX_LOWER;

  ENCODED       = PERCENT % EncodedStart XDIGIT XDIGIT;
  UNRESERVED    = alpha | digit | "-" | "." | "_" | "~";

  CHARACTER     = UNRESERVED % WordUnreserved | ENCODED % WordEncoded;
  WORD          = CHARACTER+ > WordStart;

  table         = WORD % Table;

  column        = WORD % Column;
  columns       = column (COMMA column)*;

  scope         = WORD % Scope;

  filter_and    = "and"      % { pushFilter(Filter.Op.AND); };
  filter_or     = "or"       % { pushFilter(Filter.Op.OR);  };
  filter_not    = "not"      % { pushFilter(Filter.Op.NOT); };

  filter_lt     = "lt"       % { pushFilter(Filter.Op.LT); };
  filter_le     = "le"       % { pushFilter(Filter.Op.LE); };
  filter_eq     = "eq"       % { pushFilter(Filter.Op.EQ); };
  filter_ne     = "ne"       % { pushFilter(Filter.Op.NE); };
  filter_gt     = "gt"       % { pushFilter(Filter.Op.GT); };
  filter_ge     = "ge"       % { pushFilter(Filter.Op.GE); };

  filter_lit    = COMMA WORD % { addLiteral(getWord());   };

  filter_bool   = filter_and | filter_or | filter_not;
  filter_rel    = filter_lt | filter_le | filter_eq | filter_ne | filter_gt | filter_ge;

  filter        = LPAREN @ FilterCompStart;
  filters       = filter+;

  filter_comp  := (filter_bool filters | filter_rel filter_lit {2}) RPAREN @ FilterCompEnd;

  optional_key  = WORD % OptionalKey;
  required_key  = EXCLAMATION WORD % RequiredKey;
  value         = WORD % Value;
  param         = (required_key | optional_key) (EQUALS value)? % Param;
  params        = param (COMMA param)*;


  main := table? (QUESTION columns? 
		  (QUESTION scope? 
		   (QUESTION filter? 
		    (QUESTION params? )? )? )? )?;
}%%

public class DBReference {

  public static class ParseException
    extends java.net.URISyntaxException {

    public ParseException(String input, String reason, int index) {
      super(input, reason, index);
    }

    public ParseException(String input, String reason) {
      this(input, reason, -1);
    }

    private static final long serialVersionUID = -3103596293445821757L;
  }

  public enum Scope {
    SCALAR, ROW, DISTINCT, ALL;
  }

  public static class Filter {
    public enum Op { AND, OR, NOT, LT, LE, EQ, NE, GT, GE, VAL };

    Filter(Op op) {
      this.op  = op;
      children = new LinkedList<Filter>();
    }

    Filter(String v) {
      this.op = Op.VAL;
      value   = v;
    }

    void addChild(Filter f) {
      children.add(f);
    }

    public String toString() {
      StringBuilder sb = new StringBuilder();
      toString(sb);
      return sb.toString();
    }

    private void toString(StringBuilder sb) {
      switch (op) {
	case AND:
	case OR:
	case NOT:
	case LT:
	case LE:
	case EQ:
	case NE:
	case GT:
	case GE:
	  sb.append('(').append(op.toString().toLowerCase());
	  for (Filter c : children) {
	    c.toString(sb);
	  }
	  sb.append(')');
	  break;

	case VAL:
	  sb.append(",").append(value);
	  break;
      }
    }

    public Op getOp() {
      return op;
    }

    public String getValue() {
      return value;
    }

    public List<Filter> getChildren() {
      return children;
    }

    private Op op;
    private String value;
    private List<Filter> children;
  }

  public static void main(String[] args) 
    throws Exception {

    for (String a: args) {
      System.out.println("Parsing dbref " + a);
      DBReference dbf = new DBReference(a);
      System.out.println(dbf);
    }
  }

  public DBReference(String dbref) 
    throws ParseException {
    filterStack.push(new Filter(Filter.Op.AND)); // Dummy top-level filter

    try {
      parseReference(dbref.getBytes("UTF-8"));
    }
    catch (java.io.UnsupportedEncodingException ex) {
      throw new ParseException(dbref, ex.getMessage());
    }
  }

  public String getTable() {
    return table;
  }

  public List<String> getColumns() {
    return columns;
  }

  public Scope getScope() {
    return scope;
  }

  public Filter getFilter() {
    List<Filter> children = filterStack.peek().getChildren();

    return children.isEmpty() ? null : children.get(0);
  }

  public Map<String, String> getOptionalParams() {
    return optionalParams;
  }

  public Map<String, String> getRequiredParams() {
    return requiredParams;
  }

  public String toString() {
    Filter filter = getFilter();
    return "[DBReference: " + table + "?" + join(columns) + "?" + scope.toString().toLowerCase() 
      + (filter == null ? "" : "?" + filter) + "]";
  }

  private String join(List<String> c) {
    StringBuilder sb = new StringBuilder();

    for (String s : c) {
      if (sb.length() != 0) {
	sb.append(',');
      }

      sb.append(s);
    }

    return sb.toString();
  }

  @SuppressWarnings("fallthrough")
  private void parseReference(byte[] data)
    throws ParseException, java.io.UnsupportedEncodingException {
    int cs;
    int p  = 0;
    int pe = data.length;
    int eof = pe;

    %% write init;
    %% write exec;

    if (cs < dbpath_first_final) {
      throw new ParseException(new String(data, "UTF-8"), 
			       "Failed to parse DB reference", charOffset);
    }
  }

  private void appendWordByte(byte b) {
    if (wordOffset == word.length) {
      byte[] old = word;
      word = new byte[old.length * 2];
      System.arraycopy(old, 0, word, 0, old.length);
    }
    
    word[wordOffset++] = b;
  }

  private String getWord()
    throws java.io.UnsupportedEncodingException {
    return new String(word, 0, wordOffset, "UTF-8");
  }

  private void pushFilter(Filter.Op op) {
    Filter f = new Filter(op);
    addFilter(f);
    filterStack.push(f);
  }

  private void popFilter() {
    filterStack.pop();
  }

  private void addFilter(Filter f) {
    filterStack.peek().addChild(f);
  }

  private void addLiteral(String lit) {
    addFilter(new Filter(lit));
  }

  %% write data;

  private int charOffset = 0;

  private byte[] word = new byte[256];
  private int wordOffset = 0;

  private int encoded;

  private int top;
  private int[] stack = new int[1];
  private Deque<Filter> filterStack = new LinkedList<Filter>();

  private boolean paramRequired;
  private String tmpKey;
  private String tmpValue;
  private Filter.Op filterOp;

  private String table;
  private List<String> columns = new LinkedList<String>();
  private Scope scope = Scope.ALL;
  private Map<String,String> optionalParams = new TreeMap<String,String>();
  private Map<String,String> requiredParams = new TreeMap<String,String>();
}
