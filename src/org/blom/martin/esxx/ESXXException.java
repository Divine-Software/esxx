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

/** A runtime exception that indicates that the ESXX processing has
  * failed. Unless the ECMAscript code catches the exception,
  * processing of the current request will be aborted. */

public class ESXXException 
  extends RuntimeException {
    public ESXXException(String why) { 
      super(why);
      statusCode = 500;
    }

    public ESXXException(String why, Throwable cause) { 
      super(why, cause); 
      statusCode = 500;
    }

    public ESXXException(int status, String why) { 
      super(why); 
      statusCode = status;
    }

    public ESXXException(int status, String why, Throwable cause) { 
      super(why, cause); 
      statusCode = status;
    }

    public int getStatus() {
      return statusCode;
    }

    private int statusCode;
}
