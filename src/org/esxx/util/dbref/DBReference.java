
// line 1 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"

package org.esxx.util.dbref;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Deque;
import java.util.LinkedList;
import java.util.TreeMap;


// line 195 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"


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

    
// line 186 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.java"
	{
	cs = dbpath_start;
	top = 0;
	}

// line 364 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
    
// line 194 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.java"
	{
	int _klen;
	int _trans = 0;
	int _acts;
	int _nacts;
	int _keys;
	int _goto_targ = 0;

	_goto: while (true) {
	switch ( _goto_targ ) {
	case 0:
	if ( p == pe ) {
		_goto_targ = 4;
		continue _goto;
	}
	if ( cs == 0 ) {
		_goto_targ = 5;
		continue _goto;
	}
case 1:
	_match: do {
	_keys = _dbpath_key_offsets[cs];
	_trans = _dbpath_index_offsets[cs];
	_klen = _dbpath_single_lengths[cs];
	if ( _klen > 0 ) {
		int _lower = _keys;
		int _mid;
		int _upper = _keys + _klen - 1;
		while (true) {
			if ( _upper < _lower )
				break;

			_mid = _lower + ((_upper-_lower) >> 1);
			if ( data[p] < _dbpath_trans_keys[_mid] )
				_upper = _mid - 1;
			else if ( data[p] > _dbpath_trans_keys[_mid] )
				_lower = _mid + 1;
			else {
				_trans += (_mid - _keys);
				break _match;
			}
		}
		_keys += _klen;
		_trans += _klen;
	}

	_klen = _dbpath_range_lengths[cs];
	if ( _klen > 0 ) {
		int _lower = _keys;
		int _mid;
		int _upper = _keys + (_klen<<1) - 2;
		while (true) {
			if ( _upper < _lower )
				break;

			_mid = _lower + (((_upper-_lower) >> 1) & ~1);
			if ( data[p] < _dbpath_trans_keys[_mid] )
				_upper = _mid - 2;
			else if ( data[p] > _dbpath_trans_keys[_mid+1] )
				_lower = _mid + 2;
			else {
				_trans += ((_mid - _keys)>>1);
				break _match;
			}
		}
		_trans += _klen;
	}
	} while (false);

	cs = _dbpath_trans_targs[_trans];

	if ( _dbpath_trans_actions[_trans] != 0 ) {
		_acts = _dbpath_trans_actions[_trans];
		_nacts = (int) _dbpath_actions[_acts++];
		while ( _nacts-- > 0 )
	{
			switch ( _dbpath_actions[_acts++] )
			{
	case 0:
// line 24 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    wordOffset = 0;
  }
	break;
	case 1:
// line 28 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    byte b = data[p - 1];

    if (b > (byte) 0xbf) {
      ++charOffset; // Advance char counter if start of UTF-8 sequence
    }

    appendWordByte(b);
  }
	break;
	case 2:
// line 38 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    appendWordByte((byte) encoded);
  }
	break;
	case 3:
// line 42 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    ++charOffset;
    encoded = 0;
  }
	break;
	case 4:
// line 47 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    ++charOffset;
    encoded = encoded * 16 + data[p - 1] - '0';
  }
	break;
	case 5:
// line 52 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    ++charOffset;
    encoded = encoded * 16 + data[p - 1] - 'a' + 10;
  }
	break;
	case 6:
// line 57 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    ++charOffset;
    encoded = encoded * 16 + data[p - 1] - 'A' + 10;
  }
	break;
	case 7:
// line 62 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    table = getWord();
  }
	break;
	case 8:
// line 66 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    columns.add(getWord());
  }
	break;
	case 9:
// line 70 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    String s = getWord();

    try {
      scope = Enum.valueOf(Scope.class, s.toUpperCase());
    }
    catch (IllegalArgumentException ignored) {
      charOffset -= s.length();
      {cs = (dbpath_error); _goto_targ = 2; if (true) continue _goto;}
    }
  }
	break;
	case 10:
// line 82 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    {
    if (top == stack.length) {
      int[] old = stack;
      stack = new int[old.length * 2];
      System.arraycopy(old, 0, stack, 0, old.length);
    }
  {stack[top++] = cs; cs = 29; _goto_targ = 2; if (true) continue _goto;}}
  }
	break;
	case 11:
// line 86 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    {cs = stack[--top];_goto_targ = 2; if (true) continue _goto;}
  }
	break;
	case 12:
// line 90 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    filterStack.push(new Filter(Filter.Op.AND));
  }
	break;
	case 13:
// line 94 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    filterStack.push(new Filter(Filter.Op.OR));
  }
	break;
	case 14:
// line 98 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    filterStack.push(new Filter(Filter.Op.NOT));
  }
	break;
	case 15:
// line 102 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    filterStack.push(new Filter(filterOp, tmpKey, tmpValue));
  }
	break;
	case 16:
// line 106 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    Filter child = filterStack.pop();
    filterStack.peek().addChild(child);
  }
	break;
	case 17:
// line 111 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    tmpKey = getWord();
    paramRequired = false;
  }
	break;
	case 18:
// line 116 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    tmpKey = getWord();
    paramRequired = true;
  }
	break;
	case 19:
// line 121 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    tmpValue = getWord();
  }
	break;
	case 20:
// line 125 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    if (paramRequired) {
      requiredParams.put(tmpKey, tmpValue);
    }
    else {
      optionalParams.put(tmpKey, tmpValue);
    }
  }
	break;
	case 21:
// line 169 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{ tmpKey = getWord();   }
	break;
	case 22:
// line 170 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{ filterOp = Filter.Op.LT; }
	break;
	case 23:
// line 171 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{ filterOp = Filter.Op.LE; }
	break;
	case 24:
// line 172 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{ filterOp = Filter.Op.EQ; }
	break;
	case 25:
// line 173 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{ filterOp = Filter.Op.NE; }
	break;
	case 26:
// line 174 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{ filterOp = Filter.Op.GT; }
	break;
	case 27:
// line 175 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{ filterOp = Filter.Op.GE; }
	break;
	case 28:
// line 176 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{ tmpValue = getWord(); }
	break;
// line 463 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.java"
			}
		}
	}

case 2:
	if ( cs == 0 ) {
		_goto_targ = 5;
		continue _goto;
	}
	if ( ++p != pe ) {
		_goto_targ = 1;
		continue _goto;
	}
case 4:
	if ( p == eof )
	{
	int __acts = _dbpath_eof_actions[cs];
	int __nacts = (int) _dbpath_actions[__acts++];
	while ( __nacts-- > 0 ) {
		switch ( _dbpath_actions[__acts++] ) {
	case 1:
// line 28 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    byte b = data[p - 1];

    if (b > (byte) 0xbf) {
      ++charOffset; // Advance char counter if start of UTF-8 sequence
    }

    appendWordByte(b);
  }
	break;
	case 2:
// line 38 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    appendWordByte((byte) encoded);
  }
	break;
	case 4:
// line 47 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    ++charOffset;
    encoded = encoded * 16 + data[p - 1] - '0';
  }
	break;
	case 5:
// line 52 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    ++charOffset;
    encoded = encoded * 16 + data[p - 1] - 'a' + 10;
  }
	break;
	case 6:
// line 57 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    ++charOffset;
    encoded = encoded * 16 + data[p - 1] - 'A' + 10;
  }
	break;
	case 7:
// line 62 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    table = getWord();
  }
	break;
	case 8:
// line 66 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    columns.add(getWord());
  }
	break;
	case 9:
// line 70 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    String s = getWord();

    try {
      scope = Enum.valueOf(Scope.class, s.toUpperCase());
    }
    catch (IllegalArgumentException ignored) {
      charOffset -= s.length();
      {cs = (dbpath_error); _goto_targ = 2; if (true) continue _goto;}
    }
  }
	break;
	case 17:
// line 111 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    tmpKey = getWord();
    paramRequired = false;
  }
	break;
	case 18:
// line 116 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    tmpKey = getWord();
    paramRequired = true;
  }
	break;
	case 19:
// line 121 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    tmpValue = getWord();
  }
	break;
	case 20:
// line 125 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"
	{
    if (paramRequired) {
      requiredParams.put(tmpKey, tmpValue);
    }
    else {
      optionalParams.put(tmpKey, tmpValue);
    }
  }
	break;
// line 580 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.java"
		}
	}
	}

case 5:
	}
	break; }
	}

// line 365 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"

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

  
// line 614 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.java"
private static byte[] init__dbpath_actions_0()
{
	return new byte [] {
	    0,    1,    0,    1,    1,    1,    3,    1,    4,    1,    5,    1,
	    6,    1,   10,    2,    1,    7,    2,    1,    8,    2,    1,    9,
	    2,    1,   17,    2,    1,   18,    2,    1,   21,    2,    4,    2,
	    2,    5,    2,    2,    6,    2,    2,   12,   10,    2,   13,   10,
	    2,   14,   10,    2,   16,   11,    2,   22,    0,    2,   23,    0,
	    2,   24,    0,    2,   25,    0,    2,   26,    0,    2,   27,    0,
	    3,    1,   17,   20,    3,    1,   18,   20,    3,    1,   19,   20,
	    3,    4,    2,    7,    3,    4,    2,    8,    3,    4,    2,    9,
	    3,    4,    2,   17,    3,    4,    2,   18,    3,    4,    2,   21,
	    3,    5,    2,    7,    3,    5,    2,    8,    3,    5,    2,    9,
	    3,    5,    2,   17,    3,    5,    2,   18,    3,    5,    2,   21,
	    3,    6,    2,    7,    3,    6,    2,    8,    3,    6,    2,    9,
	    3,    6,    2,   17,    3,    6,    2,   18,    3,    6,    2,   21,
	    4,    4,    2,   17,   20,    4,    4,    2,   18,   20,    4,    4,
	    2,   19,   20,    4,    5,    2,   17,   20,    4,    5,    2,   18,
	   20,    4,    5,    2,   19,   20,    4,    6,    2,   17,   20,    4,
	    6,    2,   18,   20,    4,    6,    2,   19,   20,    5,    1,   28,
	   15,   16,   11,    6,    4,    2,   28,   15,   16,   11,    6,    5,
	    2,   28,   15,   16,   11,    6,    6,    2,   28,   15,   16,   11
	};
}

private static final byte _dbpath_actions[] = init__dbpath_actions_0();


private static short[] init__dbpath_key_offsets_0()
{
	return new short [] {
	    0,    0,    6,   12,   18,   24,   35,   41,   47,   58,   64,   70,
	   82,   88,   94,  105,  111,  117,  123,  129,  135,  141,  147,  153,
	  159,  165,  171,  177,  183,  189,  201,  202,  203,  209,  215,  229,
	  230,  241,  247,  253,  265,  277,  289,  301,  307,  313,  317,  318,
	  319,  330,  332,  333,  344,  345,  356,  358,  359,  370,  371,  382,
	  383,  384,  398,  412,  426,  432,  438,  439,  441,  442,  454,  466,
	  478,  490,  503,  516,  528,  540,  552,  554,  555,  567,  580,  593,
	  606,  618,  630,  642,  654,  667,  680,  693,  706,  719,  731,  743,
	  756,  769,  781,  793
	};
}

private static final short _dbpath_key_offsets[] = init__dbpath_key_offsets_0();


private static byte[] init__dbpath_trans_keys_0()
{
	return new byte [] {
	   48,   57,   65,   70,   97,  102,   48,   57,   65,   70,   97,  102,
	   48,   57,   65,   70,   97,  102,   48,   57,   65,   70,   97,  102,
	   37,   95,  126,   45,   46,   48,   57,   65,   90,   97,  122,   48,
	   57,   65,   70,   97,  102,   48,   57,   65,   70,   97,  102,   37,
	   95,  126,   45,   46,   48,   57,   65,   90,   97,  122,   48,   57,
	   65,   70,   97,  102,   48,   57,   65,   70,   97,  102,   33,   37,
	   95,  126,   45,   46,   48,   57,   65,   90,   97,  122,   48,   57,
	   65,   70,   97,  102,   48,   57,   65,   70,   97,  102,   37,   95,
	  126,   45,   46,   48,   57,   65,   90,   97,  122,   48,   57,   65,
	   70,   97,  102,   48,   57,   65,   70,   97,  102,   48,   57,   65,
	   70,   97,  102,   48,   57,   65,   70,   97,  102,   48,   57,   65,
	   70,   97,  102,   48,   57,   65,   70,   97,  102,   48,   57,   65,
	   70,   97,  102,   48,   57,   65,   70,   97,  102,   48,   57,   65,
	   70,   97,  102,   48,   57,   65,   70,   97,  102,   48,   57,   65,
	   70,   97,  102,   48,   57,   65,   70,   97,  102,   48,   57,   65,
	   70,   97,  102,   48,   57,   65,   70,   97,  102,   33,   37,   38,
	   47,   95,  126,   45,   57,   65,   90,   97,  122,   40,   41,   48,
	   57,   65,   70,   97,  102,   48,   57,   65,   70,   97,  102,   33,
	   36,   37,   61,   95,  126,   45,   46,   48,   57,   65,   90,   97,
	  122,   61,   37,   95,  126,   45,   46,   48,   57,   65,   90,   97,
	  122,   48,   57,   65,   70,   97,  102,   48,   57,   65,   70,   97,
	  102,   37,   41,   95,  126,   45,   46,   48,   57,   65,   90,   97,
	  122,   37,   41,   95,  126,   45,   46,   48,   57,   65,   90,   97,
	  122,   37,   41,   95,  126,   45,   46,   48,   57,   65,   90,   97,
	  122,   37,   41,   95,  126,   45,   46,   48,   57,   65,   90,   97,
	  122,   48,   57,   65,   70,   97,  102,   48,   57,   65,   70,   97,
	  102,  101,  103,  108,  110,  113,   36,   37,   95,  126,   45,   46,
	   48,   57,   65,   90,   97,  122,  101,  116,   36,   37,   95,  126,
	   45,   46,   48,   57,   65,   90,   97,  122,   36,   37,   95,  126,
	   45,   46,   48,   57,   65,   90,   97,  122,  101,  116,   36,   37,
	   95,  126,   45,   46,   48,   57,   65,   90,   97,  122,   36,   37,
	   95,  126,   45,   46,   48,   57,   65,   90,   97,  122,  101,   36,
	   33,   36,   37,   61,   95,  126,   45,   46,   48,   57,   65,   90,
	   97,  122,   33,   36,   37,   61,   95,  126,   45,   46,   48,   57,
	   65,   90,   97,  122,   33,   36,   37,   61,   95,  126,   45,   46,
	   48,   57,   65,   90,   97,  122,   48,   57,   65,   70,   97,  102,
	   48,   57,   65,   70,   97,  102,   40,   40,   41,   40,   37,   63,
	   95,  126,   45,   46,   48,   57,   65,   90,   97,  122,   37,   63,
	   95,  126,   45,   46,   48,   57,   65,   90,   97,  122,   37,   63,
	   95,  126,   45,   46,   48,   57,   65,   90,   97,  122,   37,   63,
	   95,  126,   45,   46,   48,   57,   65,   90,   97,  122,   37,   44,
	   63,   95,  126,   45,   46,   48,   57,   65,   90,   97,  122,   37,
	   44,   63,   95,  126,   45,   46,   48,   57,   65,   90,   97,  122,
	   37,   63,   95,  126,   45,   46,   48,   57,   65,   90,   97,  122,
	   37,   63,   95,  126,   45,   46,   48,   57,   65,   90,   97,  122,
	   37,   63,   95,  126,   45,   46,   48,   57,   65,   90,   97,  122,
	   40,   63,   63,   33,   37,   95,  126,   45,   46,   48,   57,   65,
	   90,   97,  122,   37,   44,   61,   95,  126,   45,   46,   48,   57,
	   65,   90,   97,  122,   37,   44,   61,   95,  126,   45,   46,   48,
	   57,   65,   90,   97,  122,   37,   44,   61,   95,  126,   45,   46,
	   48,   57,   65,   90,   97,  122,   37,   44,   95,  126,   45,   46,
	   48,   57,   65,   90,   97,  122,   37,   44,   95,  126,   45,   46,
	   48,   57,   65,   90,   97,  122,   37,   44,   95,  126,   45,   46,
	   48,   57,   65,   90,   97,  122,   37,   44,   95,  126,   45,   46,
	   48,   57,   65,   90,   97,  122,   37,   44,   61,   95,  126,   45,
	   46,   48,   57,   65,   90,   97,  122,   37,   44,   61,   95,  126,
	   45,   46,   48,   57,   65,   90,   97,  122,   37,   44,   61,   95,
	  126,   45,   46,   48,   57,   65,   90,   97,  122,   37,   44,   61,
	   95,  126,   45,   46,   48,   57,   65,   90,   97,  122,   37,   44,
	   61,   95,  126,   45,   46,   48,   57,   65,   90,   97,  122,   37,
	   63,   95,  126,   45,   46,   48,   57,   65,   90,   97,  122,   37,
	   63,   95,  126,   45,   46,   48,   57,   65,   90,   97,  122,   37,
	   44,   63,   95,  126,   45,   46,   48,   57,   65,   90,   97,  122,
	   37,   44,   63,   95,  126,   45,   46,   48,   57,   65,   90,   97,
	  122,   37,   63,   95,  126,   45,   46,   48,   57,   65,   90,   97,
	  122,   37,   63,   95,  126,   45,   46,   48,   57,   65,   90,   97,
	  122,    0
	};
}

private static final byte _dbpath_trans_keys[] = init__dbpath_trans_keys_0();


private static byte[] init__dbpath_single_lengths_0()
{
	return new byte [] {
	    0,    0,    0,    0,    0,    3,    0,    0,    3,    0,    0,    4,
	    0,    0,    3,    0,    0,    0,    0,    0,    0,    0,    0,    0,
	    0,    0,    0,    0,    0,    6,    1,    1,    0,    0,    6,    1,
	    3,    0,    0,    4,    4,    4,    4,    0,    0,    4,    1,    1,
	    3,    2,    1,    3,    1,    3,    2,    1,    3,    1,    3,    1,
	    1,    6,    6,    6,    0,    0,    1,    2,    1,    4,    4,    4,
	    4,    5,    5,    4,    4,    4,    2,    1,    4,    5,    5,    5,
	    4,    4,    4,    4,    5,    5,    5,    5,    5,    4,    4,    5,
	    5,    4,    4,    0
	};
}

private static final byte _dbpath_single_lengths[] = init__dbpath_single_lengths_0();


private static byte[] init__dbpath_range_lengths_0()
{
	return new byte [] {
	    0,    3,    3,    3,    3,    4,    3,    3,    4,    3,    3,    4,
	    3,    3,    4,    3,    3,    3,    3,    3,    3,    3,    3,    3,
	    3,    3,    3,    3,    3,    3,    0,    0,    3,    3,    4,    0,
	    4,    3,    3,    4,    4,    4,    4,    3,    3,    0,    0,    0,
	    4,    0,    0,    4,    0,    4,    0,    0,    4,    0,    4,    0,
	    0,    4,    4,    4,    3,    3,    0,    0,    0,    4,    4,    4,
	    4,    4,    4,    4,    4,    4,    0,    0,    4,    4,    4,    4,
	    4,    4,    4,    4,    4,    4,    4,    4,    4,    4,    4,    4,
	    4,    4,    4,    0
	};
}

private static final byte _dbpath_range_lengths[] = init__dbpath_range_lengths_0();


private static short[] init__dbpath_index_offsets_0()
{
	return new short [] {
	    0,    0,    4,    8,   12,   16,   24,   28,   32,   40,   44,   48,
	   57,   61,   65,   73,   77,   81,   85,   89,   93,   97,  101,  105,
	  109,  113,  117,  121,  125,  129,  139,  141,  143,  147,  151,  162,
	  164,  172,  176,  180,  189,  198,  207,  216,  220,  224,  229,  231,
	  233,  241,  244,  246,  254,  256,  264,  267,  269,  277,  279,  287,
	  289,  291,  302,  313,  324,  328,  332,  334,  337,  339,  348,  357,
	  366,  375,  385,  395,  404,  413,  422,  425,  427,  436,  446,  456,
	  466,  475,  484,  493,  502,  512,  522,  532,  542,  552,  561,  570,
	  580,  590,  599,  608
	};
}

private static final short _dbpath_index_offsets[] = init__dbpath_index_offsets_0();


private static byte[] init__dbpath_trans_targs_0()
{
	return new byte [] {
	    2,   27,   28,    0,   70,   97,   98,    0,    4,   25,   26,    0,
	   73,   95,   96,    0,    3,   74,   74,   74,   74,   74,   74,    0,
	    7,   23,   24,    0,   76,   93,   94,    0,    9,   90,   90,   90,
	   90,   90,   90,    0,   10,   21,   22,    0,   81,   91,   92,    0,
	    8,   12,   83,   83,   83,   83,   83,   83,    0,   13,   19,   20,
	    0,   82,   88,   89,    0,   15,   85,   85,   85,   85,   85,   85,
	    0,   16,   17,   18,    0,   84,   86,   87,    0,   84,   86,   87,
	    0,   84,   86,   87,    0,   82,   88,   89,    0,   82,   88,   89,
	    0,   81,   91,   92,    0,   81,   91,   92,    0,   76,   93,   94,
	    0,   76,   93,   94,    0,   73,   95,   96,    0,   73,   95,   96,
	    0,   70,   97,   98,    0,   70,   97,   98,    0,   30,   32,   66,
	   68,   61,   61,   61,   61,   61,    0,   31,    0,   99,    0,   33,
	   64,   65,    0,   34,   62,   63,    0,   35,   45,   32,   48,   61,
	   61,   61,   61,   61,   61,    0,   36,    0,   37,   40,   40,   40,
	   40,   40,   40,    0,   38,   43,   44,    0,   39,   41,   42,    0,
	   37,   99,   40,   40,   40,   40,   40,   40,    0,   37,   99,   40,
	   40,   40,   40,   40,   40,    0,   37,   99,   40,   40,   40,   40,
	   40,   40,    0,   37,   99,   40,   40,   40,   40,   40,   40,    0,
	   39,   41,   42,    0,   39,   41,   42,    0,   46,   49,   54,   59,
	    0,   47,    0,   48,    0,   37,   40,   40,   40,   40,   40,   40,
	    0,   50,   52,    0,   51,    0,   37,   40,   40,   40,   40,   40,
	   40,    0,   53,    0,   37,   40,   40,   40,   40,   40,   40,    0,
	   55,   57,    0,   56,    0,   37,   40,   40,   40,   40,   40,   40,
	    0,   58,    0,   37,   40,   40,   40,   40,   40,   40,    0,   60,
	    0,   36,    0,   35,   45,   32,   48,   61,   61,   61,   61,   61,
	   61,    0,   35,   45,   32,   48,   61,   61,   61,   61,   61,   61,
	    0,   35,   45,   32,   48,   61,   61,   61,   61,   61,   61,    0,
	   34,   62,   63,    0,   34,   62,   63,    0,   67,    0,   67,   99,
	    0,   67,    0,    1,   72,   71,   71,   71,   71,   71,   71,    0,
	    1,   72,   71,   71,   71,   71,   71,   71,    0,    1,   72,   71,
	   71,   71,   71,   71,   71,    0,    3,   75,   74,   74,   74,   74,
	   74,   74,    0,    3,    5,   75,   74,   74,   74,   74,   74,   74,
	    0,    3,    5,   75,   74,   74,   74,   74,   74,   74,    0,    6,
	   78,   77,   77,   77,   77,   77,   77,    0,    6,   78,   77,   77,
	   77,   77,   77,   77,    0,    6,   78,   77,   77,   77,   77,   77,
	   77,    0,   79,   80,    0,   80,    0,    8,   12,   83,   83,   83,
	   83,   83,   83,    0,    9,   11,   14,   90,   90,   90,   90,   90,
	   90,    0,   12,   11,   14,   83,   83,   83,   83,   83,   83,    0,
	   12,   11,   14,   83,   83,   83,   83,   83,   83,    0,   15,   11,
	   85,   85,   85,   85,   85,   85,    0,   15,   11,   85,   85,   85,
	   85,   85,   85,    0,   15,   11,   85,   85,   85,   85,   85,   85,
	    0,   15,   11,   85,   85,   85,   85,   85,   85,    0,   12,   11,
	   14,   83,   83,   83,   83,   83,   83,    0,   12,   11,   14,   83,
	   83,   83,   83,   83,   83,    0,    9,   11,   14,   90,   90,   90,
	   90,   90,   90,    0,    9,   11,   14,   90,   90,   90,   90,   90,
	   90,    0,    9,   11,   14,   90,   90,   90,   90,   90,   90,    0,
	    6,   78,   77,   77,   77,   77,   77,   77,    0,    6,   78,   77,
	   77,   77,   77,   77,   77,    0,    3,    5,   75,   74,   74,   74,
	   74,   74,   74,    0,    3,    5,   75,   74,   74,   74,   74,   74,
	   74,    0,    1,   72,   71,   71,   71,   71,   71,   71,    0,    1,
	   72,   71,   71,   71,   71,   71,   71,    0,    0,    0
	};
}

private static final byte _dbpath_trans_targs[] = init__dbpath_trans_targs_0();


private static short[] init__dbpath_trans_actions_0()
{
	return new short [] {
	    5,    5,    5,    0,    7,    7,    7,    0,    5,    5,    5,    0,
	    7,    7,    7,    0,    1,    1,    1,    1,    1,    1,    1,    0,
	    5,    5,    5,    0,    7,    7,    7,    0,    1,    1,    1,    1,
	    1,    1,    1,    0,    5,    5,    5,    0,    7,    7,    7,    0,
	    0,    1,    1,    1,    1,    1,    1,    1,    0,    5,    5,    5,
	    0,    7,    7,    7,    0,    1,    1,    1,    1,    1,    1,    1,
	    0,    5,    5,    5,    0,    7,    7,    7,    0,   11,   11,   11,
	    0,    9,    9,    9,    0,   11,   11,   11,    0,    9,    9,    9,
	    0,   11,   11,   11,    0,    9,    9,    9,    0,   11,   11,   11,
	    0,    9,    9,    9,    0,   11,   11,   11,    0,    9,    9,    9,
	    0,   11,   11,   11,    0,    9,    9,    9,    0,    0,    1,    0,
	    0,    1,    1,    1,    1,    1,    0,   48,    0,   51,    0,    5,
	    5,    5,    0,    7,    7,    7,    0,  104,  104,   33,  104,   33,
	   33,   33,   33,   33,   33,    0,    0,    0,   63,   63,   63,   63,
	   63,   63,   63,    0,    5,    5,    5,    0,    7,    7,    7,    0,
	   33,  207,   33,   33,   33,   33,   33,   33,    0,    3,  201,    3,
	    3,    3,    3,    3,    3,    0,   39,  221,   39,   39,   39,   39,
	   39,   39,    0,   36,  214,   36,   36,   36,   36,   36,   36,    0,
	   11,   11,   11,    0,    9,    9,    9,    0,    0,    0,    0,    0,
	    0,    0,    0,    0,    0,   60,   60,   60,   60,   60,   60,   60,
	    0,    0,    0,    0,    0,    0,   69,   69,   69,   69,   69,   69,
	   69,    0,    0,    0,   66,   66,   66,   66,   66,   66,   66,    0,
	    0,    0,    0,    0,    0,   57,   57,   57,   57,   57,   57,   57,
	    0,    0,    0,   54,   54,   54,   54,   54,   54,   54,    0,    0,
	    0,    0,    0,   30,   30,    3,   30,    3,    3,    3,    3,    3,
	    3,    0,  152,  152,   39,  152,   39,   39,   39,   39,   39,   39,
	    0,  128,  128,   36,  128,   36,   36,   36,   36,   36,   36,    0,
	   11,   11,   11,    0,    9,    9,    9,    0,   42,    0,   13,   51,
	    0,   45,    0,    1,    0,    1,    1,    1,    1,    1,    1,    0,
	   33,   84,   33,   33,   33,   33,   33,   33,    0,    3,   15,    3,
	    3,    3,    3,    3,    3,    0,    1,    0,    1,    1,    1,    1,
	    1,    1,    0,   33,   88,   88,   33,   33,   33,   33,   33,   33,
	    0,    3,   18,   18,    3,    3,    3,    3,    3,    3,    0,    1,
	    0,    1,    1,    1,    1,    1,    1,    0,   33,   92,   33,   33,
	   33,   33,   33,   33,    0,    3,   21,    3,    3,    3,    3,    3,
	    3,    0,   13,    0,    0,    0,    0,    0,    1,    1,    1,    1,
	    1,    1,    1,    0,   33,  161,  100,   33,   33,   33,   33,   33,
	   33,    0,   33,  156,   96,   33,   33,   33,   33,   33,   33,    0,
	    3,   72,   24,    3,    3,    3,    3,    3,    3,    0,   33,  166,
	   33,   33,   33,   33,   33,   33,    0,    3,   80,    3,    3,    3,
	    3,    3,    3,    0,   39,  196,   39,   39,   39,   39,   39,   39,
	    0,   36,  181,   36,   36,   36,   36,   36,   36,    0,   39,  186,
	  144,   39,   39,   39,   39,   39,   39,    0,   36,  171,  120,   36,
	   36,   36,   36,   36,   36,    0,    3,   76,   27,    3,    3,    3,
	    3,    3,    3,    0,   39,  191,  148,   39,   39,   39,   39,   39,
	   39,    0,   36,  176,  124,   36,   36,   36,   36,   36,   36,    0,
	   39,  140,   39,   39,   39,   39,   39,   39,    0,   36,  116,   36,
	   36,   36,   36,   36,   36,    0,   39,  136,  136,   39,   39,   39,
	   39,   39,   39,    0,   36,  112,  112,   36,   36,   36,   36,   36,
	   36,    0,   39,  132,   39,   39,   39,   39,   39,   39,    0,   36,
	  108,   36,   36,   36,   36,   36,   36,    0,    0,    0
	};
}

private static final short _dbpath_trans_actions[] = init__dbpath_trans_actions_0();


private static short[] init__dbpath_eof_actions_0()
{
	return new short [] {
	    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
	    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
	    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
	    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
	    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,
	    0,    0,    0,    0,    0,    0,    0,    0,    0,    0,   84,   15,
	    0,   88,   18,    0,   92,   21,    0,    0,    0,  161,  156,   72,
	  166,   80,  196,  181,  186,  171,   76,  191,  176,  140,  116,  136,
	  112,  132,  108,    0
	};
}

private static final short _dbpath_eof_actions[] = init__dbpath_eof_actions_0();


static final int dbpath_start = 69;
static final int dbpath_first_final = 69;
static final int dbpath_error = 0;

static final int dbpath_en_filter_comp = 29;
static final int dbpath_en_main = 69;


// line 388 "/home/martin/source/esxx/src/org/esxx/util/dbref/DBReference.rl"

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
