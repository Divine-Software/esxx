
// line 1 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
/*
     ESXX - The friendly ECMAscript/XML Application Server
     Copyright (C) 2007-2015 Martin Blom <martin@blom.org>

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
import java.util.HashMap;


// line 192 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"


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
    SCALAR, COLUMN, ROW, DISTINCT, ALL;
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

  public Scope getScope(Scope defaultScope) {
    return scope == null ? defaultScope : scope;
  }

  public Filter getFilter() {
    return filter;
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

    
// line 193 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.java"
	{
	cs = dbpath_start;
	top = 0;
	}

// line 351 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
    
// line 201 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.java"
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
// line 41 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{
    wordOffset = 0;
  }
	break;
	case 1:
// line 45 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{
    byte b = data[p - 1];

    if (b > (byte) 0xbf) {
      ++charOffset; // Advance char counter if start of UTF-8 sequence
    }

    appendWordByte(b);
  }
	break;
	case 2:
// line 55 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{
    appendWordByte((byte) encoded);
  }
	break;
	case 3:
// line 59 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{
    ++charOffset;
    encoded = 0;
  }
	break;
	case 4:
// line 64 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{
    ++charOffset;
    encoded = encoded * 16 + data[p - 1] - '0';
  }
	break;
	case 5:
// line 69 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{
    ++charOffset;
    encoded = encoded * 16 + data[p - 1] - 'a' + 10;
  }
	break;
	case 6:
// line 74 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{
    ++charOffset;
    encoded = encoded * 16 + data[p - 1] - 'A' + 10;
  }
	break;
	case 7:
// line 79 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{
    table = getWord();
  }
	break;
	case 8:
// line 83 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{
    columns.add(getWord());
  }
	break;
	case 9:
// line 87 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
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
// line 99 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
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
// line 103 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{
    popFilter();
    {cs = stack[--top];_goto_targ = 2; if (true) continue _goto;}
  }
	break;
	case 12:
// line 108 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{
    tmpKey   = getWord();
    tmpValue = "";
    paramRequired = false;
  }
	break;
	case 13:
// line 114 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{
    tmpKey = getWord();
    tmpValue = "";
    paramRequired = true;
  }
	break;
	case 14:
// line 120 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{
    tmpValue = getWord();
  }
	break;
	case 15:
// line 124 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{
    if (paramRequired) {
      requiredParams.put(tmpKey, tmpValue);
    }
    else {
      optionalParams.put(tmpKey, tmpValue);
    }
  }
	break;
	case 16:
// line 160 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{ pushFilter(Filter.Op.AND); }
	break;
	case 17:
// line 161 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{ pushFilter(Filter.Op.OR);  }
	break;
	case 18:
// line 162 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{ pushFilter(Filter.Op.NOT); }
	break;
	case 19:
// line 164 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{ pushFilter(Filter.Op.LT); }
	break;
	case 20:
// line 165 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{ pushFilter(Filter.Op.LE); }
	break;
	case 21:
// line 166 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{ pushFilter(Filter.Op.EQ); }
	break;
	case 22:
// line 167 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{ pushFilter(Filter.Op.NE); }
	break;
	case 23:
// line 168 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{ pushFilter(Filter.Op.GT); }
	break;
	case 24:
// line 169 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{ pushFilter(Filter.Op.GE); }
	break;
	case 25:
// line 171 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{ addLiteral(getWord());   }
	break;
// line 450 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.java"
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
// line 45 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{
    byte b = data[p - 1];

    if (b > (byte) 0xbf) {
      ++charOffset; // Advance char counter if start of UTF-8 sequence
    }

    appendWordByte(b);
  }
	break;
	case 2:
// line 55 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{
    appendWordByte((byte) encoded);
  }
	break;
	case 4:
// line 64 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{
    ++charOffset;
    encoded = encoded * 16 + data[p - 1] - '0';
  }
	break;
	case 5:
// line 69 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{
    ++charOffset;
    encoded = encoded * 16 + data[p - 1] - 'a' + 10;
  }
	break;
	case 6:
// line 74 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{
    ++charOffset;
    encoded = encoded * 16 + data[p - 1] - 'A' + 10;
  }
	break;
	case 7:
// line 79 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{
    table = getWord();
  }
	break;
	case 8:
// line 83 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{
    columns.add(getWord());
  }
	break;
	case 9:
// line 87 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
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
	case 12:
// line 108 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{
    tmpKey   = getWord();
    tmpValue = "";
    paramRequired = false;
  }
	break;
	case 13:
// line 114 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{
    tmpKey = getWord();
    tmpValue = "";
    paramRequired = true;
  }
	break;
	case 14:
// line 120 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{
    tmpValue = getWord();
  }
	break;
	case 15:
// line 124 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"
	{
    if (paramRequired) {
      requiredParams.put(tmpKey, tmpValue);
    }
    else {
      optionalParams.put(tmpKey, tmpValue);
    }
  }
	break;
// line 569 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.java"
		}
	}
	}

case 5:
	}
	break; }
	}

// line 352 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"

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
    if (filter == null) {
      // This is the top-level filter
      filter = f;
    }
    else {
      filterStack.peek().addChild(f);
    }
  }

  private void addLiteral(String lit) {
    addFilter(new Filter(lit));
  }

  
// line 627 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.java"
private static byte[] init__dbpath_actions_0()
{
	return new byte [] {
	    0,    1,    0,    1,    1,    1,    3,    1,    4,    1,    5,    1,
	    6,    1,   10,    1,   11,    1,   19,    1,   20,    1,   21,    1,
	   22,    1,   23,    1,   24,    2,    1,    7,    2,    1,    8,    2,
	    1,    9,    2,    1,   12,    2,    1,   13,    2,    1,   25,    2,
	    4,    2,    2,    5,    2,    2,    6,    2,    2,   16,   10,    2,
	   17,   10,    2,   18,   10,    3,    1,   12,   15,    3,    1,   13,
	   15,    3,    1,   14,   15,    3,    1,   25,   11,    3,    4,    2,
	    7,    3,    4,    2,    8,    3,    4,    2,    9,    3,    4,    2,
	   12,    3,    4,    2,   13,    3,    4,    2,   25,    3,    5,    2,
	    7,    3,    5,    2,    8,    3,    5,    2,    9,    3,    5,    2,
	   12,    3,    5,    2,   13,    3,    5,    2,   25,    3,    6,    2,
	    7,    3,    6,    2,    8,    3,    6,    2,    9,    3,    6,    2,
	   12,    3,    6,    2,   13,    3,    6,    2,   25,    4,    4,    2,
	   12,   15,    4,    4,    2,   13,   15,    4,    4,    2,   14,   15,
	    4,    4,    2,   25,   11,    4,    5,    2,   12,   15,    4,    5,
	    2,   13,   15,    4,    5,    2,   14,   15,    4,    5,    2,   25,
	   11,    4,    6,    2,   12,   15,    4,    6,    2,   13,   15,    4,
	    6,    2,   14,   15,    4,    6,    2,   25,   11
	};
}

private static final byte _dbpath_actions[] = init__dbpath_actions_0();


private static short[] init__dbpath_key_offsets_0()
{
	return new short [] {
	    0,    0,    6,   12,   18,   24,   35,   41,   47,   58,   64,   70,
	   82,   88,   94,  105,  111,  117,  123,  129,  135,  141,  147,  153,
	  159,  165,  171,  177,  183,  189,  195,  196,  197,  198,  200,  201,
	  202,  213,  219,  225,  237,  248,  254,  260,  272,  284,  296,  308,
	  314,  320,  332,  344,  356,  362,  368,  370,  371,  372,  374,  375,
	  376,  378,  379,  380,  381,  382,  383,  395,  407,  419,  431,  444,
	  457,  469,  481,  493,  495,  496,  508,  521,  534,  547,  559,  571,
	  583,  595,  608,  621,  634,  647,  660,  672,  684,  697,  710,  722,
	  734
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
	   70,   97,  102,   48,   57,   65,   70,   97,  102,   97,  101,  103,
	  108,  110,  111,  110,  100,   40,   40,   41,  113,   44,   37,   95,
	  126,   45,   46,   48,   57,   65,   90,   97,  122,   48,   57,   65,
	   70,   97,  102,   48,   57,   65,   70,   97,  102,   37,   44,   95,
	  126,   45,   46,   48,   57,   65,   90,   97,  122,   37,   95,  126,
	   45,   46,   48,   57,   65,   90,   97,  122,   48,   57,   65,   70,
	   97,  102,   48,   57,   65,   70,   97,  102,   37,   41,   95,  126,
	   45,   46,   48,   57,   65,   90,   97,  122,   37,   41,   95,  126,
	   45,   46,   48,   57,   65,   90,   97,  122,   37,   41,   95,  126,
	   45,   46,   48,   57,   65,   90,   97,  122,   37,   41,   95,  126,
	   45,   46,   48,   57,   65,   90,   97,  122,   48,   57,   65,   70,
	   97,  102,   48,   57,   65,   70,   97,  102,   37,   44,   95,  126,
	   45,   46,   48,   57,   65,   90,   97,  122,   37,   44,   95,  126,
	   45,   46,   48,   57,   65,   90,   97,  122,   37,   44,   95,  126,
	   45,   46,   48,   57,   65,   90,   97,  122,   48,   57,   65,   70,
	   97,  102,   48,   57,   65,   70,   97,  102,  101,  116,   44,   44,
	  101,  116,   44,   44,  101,  111,   44,  116,   40,  114,   40,   37,
	   63,   95,  126,   45,   46,   48,   57,   65,   90,   97,  122,   37,
	   63,   95,  126,   45,   46,   48,   57,   65,   90,   97,  122,   37,
	   63,   95,  126,   45,   46,   48,   57,   65,   90,   97,  122,   37,
	   63,   95,  126,   45,   46,   48,   57,   65,   90,   97,  122,   37,
	   44,   63,   95,  126,   45,   46,   48,   57,   65,   90,   97,  122,
	   37,   44,   63,   95,  126,   45,   46,   48,   57,   65,   90,   97,
	  122,   37,   63,   95,  126,   45,   46,   48,   57,   65,   90,   97,
	  122,   37,   63,   95,  126,   45,   46,   48,   57,   65,   90,   97,
	  122,   37,   63,   95,  126,   45,   46,   48,   57,   65,   90,   97,
	  122,   40,   63,   63,   33,   37,   95,  126,   45,   46,   48,   57,
	   65,   90,   97,  122,   37,   44,   61,   95,  126,   45,   46,   48,
	   57,   65,   90,   97,  122,   37,   44,   61,   95,  126,   45,   46,
	   48,   57,   65,   90,   97,  122,   37,   44,   61,   95,  126,   45,
	   46,   48,   57,   65,   90,   97,  122,   37,   44,   95,  126,   45,
	   46,   48,   57,   65,   90,   97,  122,   37,   44,   95,  126,   45,
	   46,   48,   57,   65,   90,   97,  122,   37,   44,   95,  126,   45,
	   46,   48,   57,   65,   90,   97,  122,   37,   44,   95,  126,   45,
	   46,   48,   57,   65,   90,   97,  122,   37,   44,   61,   95,  126,
	   45,   46,   48,   57,   65,   90,   97,  122,   37,   44,   61,   95,
	  126,   45,   46,   48,   57,   65,   90,   97,  122,   37,   44,   61,
	   95,  126,   45,   46,   48,   57,   65,   90,   97,  122,   37,   44,
	   61,   95,  126,   45,   46,   48,   57,   65,   90,   97,  122,   37,
	   44,   61,   95,  126,   45,   46,   48,   57,   65,   90,   97,  122,
	   37,   63,   95,  126,   45,   46,   48,   57,   65,   90,   97,  122,
	   37,   63,   95,  126,   45,   46,   48,   57,   65,   90,   97,  122,
	   37,   44,   63,   95,  126,   45,   46,   48,   57,   65,   90,   97,
	  122,   37,   44,   63,   95,  126,   45,   46,   48,   57,   65,   90,
	   97,  122,   37,   63,   95,  126,   45,   46,   48,   57,   65,   90,
	   97,  122,   37,   63,   95,  126,   45,   46,   48,   57,   65,   90,
	   97,  122,    0
	};
}

private static final byte _dbpath_trans_keys[] = init__dbpath_trans_keys_0();


private static byte[] init__dbpath_single_lengths_0()
{
	return new byte [] {
	    0,    0,    0,    0,    0,    3,    0,    0,    3,    0,    0,    4,
	    0,    0,    3,    0,    0,    0,    0,    0,    0,    0,    0,    0,
	    0,    0,    0,    0,    0,    6,    1,    1,    1,    2,    1,    1,
	    3,    0,    0,    4,    3,    0,    0,    4,    4,    4,    4,    0,
	    0,    4,    4,    4,    0,    0,    2,    1,    1,    2,    1,    1,
	    2,    1,    1,    1,    1,    1,    4,    4,    4,    4,    5,    5,
	    4,    4,    4,    2,    1,    4,    5,    5,    5,    4,    4,    4,
	    4,    5,    5,    5,    5,    5,    4,    4,    5,    5,    4,    4,
	    0
	};
}

private static final byte _dbpath_single_lengths[] = init__dbpath_single_lengths_0();


private static byte[] init__dbpath_range_lengths_0()
{
	return new byte [] {
	    0,    3,    3,    3,    3,    4,    3,    3,    4,    3,    3,    4,
	    3,    3,    4,    3,    3,    3,    3,    3,    3,    3,    3,    3,
	    3,    3,    3,    3,    3,    0,    0,    0,    0,    0,    0,    0,
	    4,    3,    3,    4,    4,    3,    3,    4,    4,    4,    4,    3,
	    3,    4,    4,    4,    3,    3,    0,    0,    0,    0,    0,    0,
	    0,    0,    0,    0,    0,    0,    4,    4,    4,    4,    4,    4,
	    4,    4,    4,    0,    0,    4,    4,    4,    4,    4,    4,    4,
	    4,    4,    4,    4,    4,    4,    4,    4,    4,    4,    4,    4,
	    0
	};
}

private static final byte _dbpath_range_lengths[] = init__dbpath_range_lengths_0();


private static short[] init__dbpath_index_offsets_0()
{
	return new short [] {
	    0,    0,    4,    8,   12,   16,   24,   28,   32,   40,   44,   48,
	   57,   61,   65,   73,   77,   81,   85,   89,   93,   97,  101,  105,
	  109,  113,  117,  121,  125,  129,  136,  138,  140,  142,  145,  147,
	  149,  157,  161,  165,  174,  182,  186,  190,  199,  208,  217,  226,
	  230,  234,  243,  252,  261,  265,  269,  272,  274,  276,  279,  281,
	  283,  286,  288,  290,  292,  294,  296,  305,  314,  323,  332,  342,
	  352,  361,  370,  379,  382,  384,  393,  403,  413,  423,  432,  441,
	  450,  459,  469,  479,  489,  499,  509,  518,  527,  537,  547,  556,
	  565
	};
}

private static final short _dbpath_index_offsets[] = init__dbpath_index_offsets_0();


private static byte[] init__dbpath_trans_targs_0()
{
	return new byte [] {
	    2,   27,   28,    0,   67,   94,   95,    0,    4,   25,   26,    0,
	   70,   92,   93,    0,    3,   71,   71,   71,   71,   71,   71,    0,
	    7,   23,   24,    0,   73,   90,   91,    0,    9,   87,   87,   87,
	   87,   87,   87,    0,   10,   21,   22,    0,   78,   88,   89,    0,
	    8,   12,   80,   80,   80,   80,   80,   80,    0,   13,   19,   20,
	    0,   79,   85,   86,    0,   15,   82,   82,   82,   82,   82,   82,
	    0,   16,   17,   18,    0,   81,   83,   84,    0,   81,   83,   84,
	    0,   81,   83,   84,    0,   79,   85,   86,    0,   79,   85,   86,
	    0,   78,   88,   89,    0,   78,   88,   89,    0,   73,   90,   91,
	    0,   73,   90,   91,    0,   70,   92,   93,    0,   70,   92,   93,
	    0,   67,   94,   95,    0,   67,   94,   95,    0,   30,   34,   54,
	   57,   60,   64,    0,   31,    0,   32,    0,   33,    0,   33,   96,
	    0,   35,    0,   36,    0,   37,   49,   49,   49,   49,   49,   49,
	    0,   38,   52,   53,    0,   39,   50,   51,    0,   37,   40,   49,
	   49,   49,   49,   49,   49,    0,   41,   44,   44,   44,   44,   44,
	   44,    0,   42,   47,   48,    0,   43,   45,   46,    0,   41,   96,
	   44,   44,   44,   44,   44,   44,    0,   41,   96,   44,   44,   44,
	   44,   44,   44,    0,   41,   96,   44,   44,   44,   44,   44,   44,
	    0,   41,   96,   44,   44,   44,   44,   44,   44,    0,   43,   45,
	   46,    0,   43,   45,   46,    0,   37,   40,   49,   49,   49,   49,
	   49,   49,    0,   37,   40,   49,   49,   49,   49,   49,   49,    0,
	   37,   40,   49,   49,   49,   49,   49,   49,    0,   39,   50,   51,
	    0,   39,   50,   51,    0,   55,   56,    0,   36,    0,   36,    0,
	   58,   59,    0,   36,    0,   36,    0,   61,   62,    0,   36,    0,
	   63,    0,   33,    0,   65,    0,   33,    0,    1,   69,   68,   68,
	   68,   68,   68,   68,    0,    1,   69,   68,   68,   68,   68,   68,
	   68,    0,    1,   69,   68,   68,   68,   68,   68,   68,    0,    3,
	   72,   71,   71,   71,   71,   71,   71,    0,    3,    5,   72,   71,
	   71,   71,   71,   71,   71,    0,    3,    5,   72,   71,   71,   71,
	   71,   71,   71,    0,    6,   75,   74,   74,   74,   74,   74,   74,
	    0,    6,   75,   74,   74,   74,   74,   74,   74,    0,    6,   75,
	   74,   74,   74,   74,   74,   74,    0,   76,   77,    0,   77,    0,
	    8,   12,   80,   80,   80,   80,   80,   80,    0,    9,   11,   14,
	   87,   87,   87,   87,   87,   87,    0,   12,   11,   14,   80,   80,
	   80,   80,   80,   80,    0,   12,   11,   14,   80,   80,   80,   80,
	   80,   80,    0,   15,   11,   82,   82,   82,   82,   82,   82,    0,
	   15,   11,   82,   82,   82,   82,   82,   82,    0,   15,   11,   82,
	   82,   82,   82,   82,   82,    0,   15,   11,   82,   82,   82,   82,
	   82,   82,    0,   12,   11,   14,   80,   80,   80,   80,   80,   80,
	    0,   12,   11,   14,   80,   80,   80,   80,   80,   80,    0,    9,
	   11,   14,   87,   87,   87,   87,   87,   87,    0,    9,   11,   14,
	   87,   87,   87,   87,   87,   87,    0,    9,   11,   14,   87,   87,
	   87,   87,   87,   87,    0,    6,   75,   74,   74,   74,   74,   74,
	   74,    0,    6,   75,   74,   74,   74,   74,   74,   74,    0,    3,
	    5,   72,   71,   71,   71,   71,   71,   71,    0,    3,    5,   72,
	   71,   71,   71,   71,   71,   71,    0,    1,   69,   68,   68,   68,
	   68,   68,   68,    0,    1,   69,   68,   68,   68,   68,   68,   68,
	    0,    0,    0
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
	    0,   11,   11,   11,    0,    9,    9,    9,    0,    0,    0,    0,
	    0,    0,    0,    0,    0,    0,    0,    0,   56,    0,   13,   15,
	    0,    0,    0,   21,    0,    1,    1,    1,    1,    1,    1,    1,
	    0,    5,    5,    5,    0,    7,    7,    7,    0,   47,  101,   47,
	   47,   47,   47,   47,   47,    0,    1,    1,    1,    1,    1,    1,
	    1,    0,    5,    5,    5,    0,    7,    7,    7,    0,   47,  168,
	   47,   47,   47,   47,   47,   47,    0,    3,   77,    3,    3,    3,
	    3,    3,    3,    0,   53,  208,   53,   53,   53,   53,   53,   53,
	    0,   50,  188,   50,   50,   50,   50,   50,   50,    0,   11,   11,
	   11,    0,    9,    9,    9,    0,    3,   44,    3,    3,    3,    3,
	    3,    3,    0,   53,  149,   53,   53,   53,   53,   53,   53,    0,
	   50,  125,   50,   50,   50,   50,   50,   50,    0,   11,   11,   11,
	    0,    9,    9,    9,    0,    0,    0,    0,   27,    0,   25,    0,
	    0,    0,    0,   19,    0,   17,    0,    0,    0,    0,   23,    0,
	    0,    0,   62,    0,    0,    0,   59,    0,    1,    0,    1,    1,
	    1,    1,    1,    1,    0,   47,   81,   47,   47,   47,   47,   47,
	   47,    0,    3,   29,    3,    3,    3,    3,    3,    3,    0,    1,
	    0,    1,    1,    1,    1,    1,    1,    0,   47,   85,   85,   47,
	   47,   47,   47,   47,   47,    0,    3,   32,   32,    3,    3,    3,
	    3,    3,    3,    0,    1,    0,    1,    1,    1,    1,    1,    1,
	    0,   47,   89,   47,   47,   47,   47,   47,   47,    0,    3,   35,
	    3,    3,    3,    3,    3,    3,    0,   13,    0,    0,    0,    0,
	    0,    1,    1,    1,    1,    1,    1,    1,    0,   47,  158,   97,
	   47,   47,   47,   47,   47,   47,    0,   47,  153,   93,   47,   47,
	   47,   47,   47,   47,    0,    3,   65,   38,    3,    3,    3,    3,
	    3,    3,    0,   47,  163,   47,   47,   47,   47,   47,   47,    0,
	    3,   73,    3,    3,    3,    3,    3,    3,    0,   53,  203,   53,
	   53,   53,   53,   53,   53,    0,   50,  183,   50,   50,   50,   50,
	   50,   50,    0,   53,  193,  141,   53,   53,   53,   53,   53,   53,
	    0,   50,  173,  117,   50,   50,   50,   50,   50,   50,    0,    3,
	   69,   41,    3,    3,    3,    3,    3,    3,    0,   53,  198,  145,
	   53,   53,   53,   53,   53,   53,    0,   50,  178,  121,   50,   50,
	   50,   50,   50,   50,    0,   53,  137,   53,   53,   53,   53,   53,
	   53,    0,   50,  113,   50,   50,   50,   50,   50,   50,    0,   53,
	  133,  133,   53,   53,   53,   53,   53,   53,    0,   50,  109,  109,
	   50,   50,   50,   50,   50,   50,    0,   53,  129,   53,   53,   53,
	   53,   53,   53,    0,   50,  105,   50,   50,   50,   50,   50,   50,
	    0,    0,    0
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
	    0,    0,    0,    0,    0,    0,    0,   81,   29,    0,   85,   32,
	    0,   89,   35,    0,    0,    0,  158,  153,   65,  163,   73,  203,
	  183,  193,  173,   69,  198,  178,  137,  113,  133,  109,  129,  105,
	    0
	};
}

private static final short _dbpath_eof_actions[] = init__dbpath_eof_actions_0();


static final int dbpath_start = 66;
static final int dbpath_first_final = 66;
static final int dbpath_error = 0;

static final int dbpath_en_filter_comp = 29;
static final int dbpath_en_main = 66;


// line 399 "/Users/leviticus/source/esxx/src/org/esxx/dbref/DBReference.rl"

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
  private Scope scope;
  private Filter filter;
  private Map<String,String> optionalParams = new HashMap<String,String>();
  private Map<String,String> requiredParams = new HashMap<String,String>();
}
