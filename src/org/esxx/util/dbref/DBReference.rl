
package org.esxx.util.dbref;

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
    fret;
  }

  action FilterAND {
    filterStack.push(new Filter(Filter.Op.AND));
  }

  action FilterOR {
    filterStack.push(new Filter(Filter.Op.OR));
  }

  action FilterNOT {
    filterStack.push(new Filter(Filter.Op.NOT));
  }

  action FilterItem {
    filterStack.push(new Filter(filterOp, tmpKey, tmpValue));
  }

  action FilterChild {
    Filter child = filterStack.pop();
    filterStack.peek().addChild(child);
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
  SLASH         = '/';

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

  filter        = LPAREN @ FilterCompStart;
  filters       = filter+;

  filter_and    = AMPERSAND % FilterAND filters;
  filter_or     = SLASH % FilterOR filters;
  filter_not    = EXCLAMATION % FilterNOT filter;

  filter_key    = WORD                          % { tmpKey = getWord();   };
  filter_lt     = "$lt$"                        % { filterOp = Filter.Op.LT; };
  filter_le     = "$le$"                        % { filterOp = Filter.Op.LE; };
  filter_eq     = ("$eq$" | EQUALS)             % { filterOp = Filter.Op.EQ; };
  filter_ne     = ("$ne$" | EXCLAMATION EQUALS) % { filterOp = Filter.Op.NE; };
  filter_gt     = "$gt$"                        % { filterOp = Filter.Op.GT; };
  filter_ge     = "$ge$"                        % { filterOp = Filter.Op.GE; };
  filter_value  = WORD                          % { tmpValue = getWord(); };

  filter_op     = filter_lt | filter_le | filter_eq | filter_ne | filter_gt | filter_ge;
  filter_item   = ( filter_key filter_op filter_value ) % FilterItem;

  filter_comp  := (filter_and | filter_or | filter_not | filter_item) % FilterChild
                  RPAREN @ FilterCompEnd;

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
    SCALAR, ONE, DISTINCT, ALL;
  }

  public static class Filter {
    public enum Op { AND, OR, NOT, LT, LE, EQ, NE, GT, GE };

    Filter(Op op) {
      this.op  = op;
      children = new LinkedList<Filter>();
    }

    Filter(Op op, String l, String r) {
      this.op = op;
      left    = l;
      right   = r;
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
	  sb.append('(').append(op).append(' ');
	  for (Filter c : children) {
	    c.toString(sb);
	  }
	  sb.append(')');
	  break;

	case LT:
	case LE:
	case EQ:
	case NE:
	case GT:
	case GE:
	  sb.append("('").append(left).append("' ")
	    .append(op)
	    .append(" '").append(right).append("')");
	  break;
      }
    }

    public Op getOp() {
      return op;
    }

    public String getLeft() {
      return left;
    }

    public String getRight() {
      return right;
    }

    public List<Filter> getChildren() {
      return children;
    }

    private Op op;
    private String left;
    private String right;
    private List<Filter> children;
  }

  public static void main(String[] args) 
    throws Exception {

    for (String a: args) {
      System.out.println("Parsing DB refernece " + a);
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
    return "[DBReference: '" + table + "' (" + join(columns) + ") " 
      + scope + " " + getFilter() + "]";
  }

  private String join(List<String> c) {
    StringBuilder sb = new StringBuilder();

    for (String s : c) {
      if (sb.length() != 0) {
	sb.append(',');
      }

      sb.append('\'').append(s).append('\'');
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
