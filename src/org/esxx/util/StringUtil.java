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

package org.esxx.util;

import org.esxx.ESXXException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.net.URISyntaxException;

public abstract class StringUtil {
  public interface ParamResolver {
    public String resolveParam(String param);
  }

  public static String format(String format, ParamResolver resolver) {
    if (format == null) {
      return null;
    }

    StringBuffer s = new StringBuffer();
    Matcher      m = paramPattern.matcher(format);

    while (m.find()) {
      String g = m.group();

      if (m.start(1) != -1) {
	// Match on group 1, which is our parameter pattern; append a single '?'
	m.appendReplacement(s, resolver.resolveParam(g.substring(1, g.length() - 1)));
      }
      else {
	// Match on quoted strings, which we just copy as-is
	m.appendReplacement(s, g);
      }
    }

    m.appendTail(s);

    return s.toString();
  }

  // TODO: Consider replacing all this with just \{ escape notation instead
  private static final String quotePattern1 = "('((\\\\')|[^'])+')";
  private static final String quotePattern2 = "(`((\\\\`)|[^`])+`)";
  private static final String quotePattern3 = "(\"((\\\\\")|[^\"])+\")";

  private static final Pattern paramPattern = 
    Pattern.compile("(\\{[^\\}]+\\})" +    // Group 1: Matches {identifier}
		    "|" + quotePattern1 + "|" + quotePattern2 + "|" + quotePattern3);


  /*
   *   ECMA 3, 15.1.3 URI Handling Function Properties
   *
   *   The following are implementations of the algorithms
   *   given in the ECMA specification for the hidden functions
   *   'Encode' and 'Decode'.
   *
   *   This method is taken from org.mozilla.javascript.NativeGlobal
   */
  public static String encodeURI(String str, boolean fullUri)
    throws URISyntaxException {
    byte[] utf8buf = null;
    StringBuffer sb = null;

    for (int k = 0, length = str.length(); k != length; ++k) {
      char C = str.charAt(k);
      if (encodeUnescaped(C, fullUri)) {
	if (sb != null) {
	  sb.append(C);
	}
      } else {
	if (sb == null) {
	  sb = new StringBuffer(length + 3);
	  sb.append(str);
	  sb.setLength(k);
	  utf8buf = new byte[6];
	}
	if (0xDC00 <= C && C <= 0xDFFF) {
	  throw new URISyntaxException(str, "Illegal URI format");
	}
	int V;
	if (C < 0xD800 || 0xDBFF < C) {
	  V = C;
	} else {
	  k++;
	  if (k == length) {
	    throw new URISyntaxException(str, "Illegal URI format");
	  }
	  char C2 = str.charAt(k);
	  if (!(0xDC00 <= C2 && C2 <= 0xDFFF)) {
	    throw new URISyntaxException(str, "Illegal URI format");
	  }
	  V = ((C - 0xD800) << 10) + (C2 - 0xDC00) + 0x10000;
	}
	int L = oneUcs4ToUtf8Char(utf8buf, V);
	for (int j = 0; j < L; j++) {
	  int d = 0xff & utf8buf[j];
	  sb.append('%');
	  sb.append(toHexChar(d >>> 4));
	  sb.append(toHexChar(d & 0xf));
	}
      }
    }
    return (sb == null) ? str : sb.toString();
  }

  private static char toHexChar(int i) {
    if (i >> 4 != 0) {
      throw new ESXXException("Totally unexpected error in StringUtil.toHexChar()");
    }

    return (char)((i < 10) ? i + '0' : i - 10 + 'A');
  }

  private static int unHex(char c) {
    if ('A' <= c && c <= 'F') {
      return c - 'A' + 10;
    } else if ('a' <= c && c <= 'f') {
      return c - 'a' + 10;
    } else if ('0' <= c && c <= '9') {
      return c - '0';
    } else {
      return -1;
    }
  }

  private static int unHex(char c1, char c2) {
    int i1 = unHex(c1);
    int i2 = unHex(c2);
    if (i1 >= 0 && i2 >= 0) {
      return (i1 << 4) | i2;
    }
    return -1;
  }

  // This method is taken from org.mozilla.javascript.NativeGlobal

  public static String decodeURI(String str, boolean fullUri)
    throws URISyntaxException {
    char[] buf = null;
    int bufTop = 0;

    for (int k = 0, length = str.length(); k != length;) {
      char C = str.charAt(k);
      if (C != '%') {
	if (buf != null) {
	  buf[bufTop++] = C;
	}
	++k;
      } else {
	if (buf == null) {
	  // decode always compress so result can not be bigger then
	  // str.length()
	  buf = new char[length];
	  str.getChars(0, k, buf, 0);
	  bufTop = k;
	}
	int start = k;
	if (k + 3 > length) {
	  throw new URISyntaxException(str, "Illegal URI format");
	}
	int B = unHex(str.charAt(k + 1), str.charAt(k + 2));
	if (B < 0) {
	  throw new URISyntaxException(str, "Illegal URI format");
	}
	k += 3;
	if ((B & 0x80) == 0) {
	  C = (char)B;
	} else {
	  // Decode UTF-8 sequence into ucs4Char and encode it into
	  // UTF-16
	  int utf8Tail, ucs4Char, minUcs4Char;
	  if ((B & 0xC0) == 0x80) {
	    // First  UTF-8 should be ouside 0x80..0xBF
	    throw new URISyntaxException(str, "Illegal URI format");
	  } else if ((B & 0x20) == 0) {
	    utf8Tail = 1; ucs4Char = B & 0x1F;
	    minUcs4Char = 0x80;
	  } else if ((B & 0x10) == 0) {
	    utf8Tail = 2; ucs4Char = B & 0x0F;
	    minUcs4Char = 0x800;
	  } else if ((B & 0x08) == 0) {
	    utf8Tail = 3; ucs4Char = B & 0x07;
	    minUcs4Char = 0x10000;
	  } else if ((B & 0x04) == 0) {
	    utf8Tail = 4; ucs4Char = B & 0x03;
	    minUcs4Char = 0x200000;
	  } else if ((B & 0x02) == 0) {
	    utf8Tail = 5; ucs4Char = B & 0x01;
	    minUcs4Char = 0x4000000;
	  } else {
	    // First UTF-8 can not be 0xFF or 0xFE
	    throw new URISyntaxException(str, "Illegal URI format");
	  }
	  if (k + 3 * utf8Tail > length) {
	    throw new URISyntaxException(str, "Illegal URI format");
	  }
	  for (int j = 0; j != utf8Tail; j++) {
	    if (str.charAt(k) != '%') {
	      throw new URISyntaxException(str, "Illegal URI format");
	    }
	    B = unHex(str.charAt(k + 1), str.charAt(k + 2));
	    if (B < 0 || (B & 0xC0) != 0x80) {
	      throw new URISyntaxException(str, "Illegal URI format");
	    }
	    ucs4Char = (ucs4Char << 6) | (B & 0x3F);
	    k += 3;
	  }
	  // Check for overlongs and other should-not-present codes
	  if (ucs4Char < minUcs4Char
	      || ucs4Char == 0xFFFE || ucs4Char == 0xFFFF)
	    {
	      ucs4Char = 0xFFFD;
	    }
	  if (ucs4Char >= 0x10000) {
	    ucs4Char -= 0x10000;
	    if (ucs4Char > 0xFFFFF) {
	      throw new URISyntaxException(str, "Illegal URI format");
	    }
	    char H = (char)((ucs4Char >>> 10) + 0xD800);
	    C = (char)((ucs4Char & 0x3FF) + 0xDC00);
	    buf[bufTop++] = H;
	  } else {
	    C = (char)ucs4Char;
	  }
	}
	if (fullUri && URI_DECODE_RESERVED.indexOf(C) >= 0) {
	  for (int x = start; x != k; x++) {
	    buf[bufTop++] = str.charAt(x);
	  }
	} else {
	  buf[bufTop++] = C;
	}
      }
    }
    return (buf == null) ? str : new String(buf, 0, bufTop);
  }

  private static boolean encodeUnescaped(char c, boolean fullUri) {
    if (('A' <= c && c <= 'Z') || ('a' <= c && c <= 'z')
	|| ('0' <= c && c <= '9'))
      {
	return true;
      }
    if ("-_.!~*'()".indexOf(c) >= 0)
      return true;
    if (fullUri) {
      return URI_DECODE_RESERVED.indexOf(c) >= 0;
    }
    return false;
  }

  private static final String URI_DECODE_RESERVED = ";/?:@&=+$,#";

  /* Convert one UCS-4 char and write it into a UTF-8 buffer, which must be
   * at least 6 bytes long.  Return the number of UTF-8 bytes of data written.
   */
  private static int oneUcs4ToUtf8Char(byte[] utf8Buffer, int ucs4Char) {
    int utf8Length = 1;

    //JS_ASSERT(ucs4Char <= 0x7FFFFFFF);
    if ((ucs4Char & ~0x7F) == 0)
      utf8Buffer[0] = (byte)ucs4Char;
    else {
      int i;
      int a = ucs4Char >>> 11;
      utf8Length = 2;
      while (a != 0) {
	a >>>= 5;
	utf8Length++;
      }
      i = utf8Length;
      while (--i > 0) {
	utf8Buffer[i] = (byte)((ucs4Char & 0x3F) | 0x80);
	ucs4Char >>>= 6;
      }
      utf8Buffer[0] = (byte)(0x100 - (1 << (8-utf8Length)) + ucs4Char);
    }
    return utf8Length;
  }
}
