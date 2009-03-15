
/*
 *  This file defines some useful stuff from EcmaScript 3.1 (aka
 * "Harmony") that is currently not available in Rhino.
 *
 * Public Domain.
 *
 */

__dontenum__ = function (o, p) {
  java.lang.Class.forName("org.mozilla.javascript.ScriptableObject")
    .getMethod("setAttributes", java.lang.String, java.lang.Integer.TYPE)
    .invoke(o, p, new java.lang.Integer(org.mozilla.javascript.ScriptableObject.DONTENUM));

  if (typeof o.constructor !== "undefined" &&
      typeof o.constructor.name !== "undefined") {
    esxx.log.debug(esxx.location.valueOf() + " added " + p + " to " + o.constructor.name);
  }
}

// Array.isArray


// Array.prototype.reduce
// https://developer.mozilla.org/en/Core_JavaScript_1.5_Reference/Objects/Array/reduce
if (typeof Array.prototype.reduce !== "function") {
  Array.prototype.reduce = function(fun /*, initial*/) {
    var len = this.length;
    if (typeof fun != "function")
      throw new TypeError();

    // no value to return if no initial value and an empty array
    if (len == 0 && arguments.length == 1) {
      throw new TypeError();
    }

    var i = 0;
    if (arguments.length >= 2) {
      var rv = arguments[1];
    }
    else {
      do {
        if (i in this) {
          rv = this[i++];
          break;
        }

        // if array contains no values, no initial value to return
        if (++i >= len) {
          throw new TypeError();
	}
      }
      while (true);
    }

    for (; i < len; i++) {
      if (i in this)
        rv = fun.call(null, rv, this[i], i, this);
    }

    return rv;
  };
  __dontenum__(Array.prototype, "reduce");
}


// Array.prototype.reduceRight
// https://developer.mozilla.org/en/Core_JavaScript_1.5_Reference/Objects/Array/reduceRight
if (typeof Array.prototype.reduceRight !== "function") {
  Array.prototype.reduceRight = function(fun /*, initial*/) {
    var len = this.length;
    if (typeof fun != "function")
      throw new TypeError();

    // no value to return if no initial value, empty array
    if (len == 0 && arguments.length == 1) {
      throw new TypeError();
    }

    var i = len - 1;
    if (arguments.length >= 2) {
      var rv = arguments[1];
    }
    else {
      do {
        if (i in this) {
          rv = this[i--];
          break;
        }

        // if array contains no values, no initial value to return
        if (--i < 0) {
          throw new TypeError();
	}
      }
      while (true);
    }

    for (; i >= 0; i--) {
      if (i in this)
        rv = fun.call(null, rv, this[i], i, this);
    }

    return rv;
  };
  __dontenum__(Array.prototype, "reduceRight");
}

// Function.prototype.bind
if (typeof Function.prototype.bind !== "function") {
  Function.prototype.bind = function (thisArg /* [, arg1[, arg2, ...]] */ ) {
    let self = this;

    return function() {
      return self.apply(thisArg, arguments);
    };
  };
  __dontenum__(Function.prototype, "bind");
}

// Date.prototype.toISOString
if (typeof Date.prototype.toISOString !== "function") {
  Date.prototype.toISOString = function () {
    function f(n) {
      // Format integers to have at least two digits.
      return n < 10 ? '0' + n : n;
    }

    return this.getUTCFullYear()   + '-' +
      f(this.getUTCMonth() + 1) + '-' +
      f(this.getUTCDate())      + 'T' +
      f(this.getUTCHours())     + ':' +
      f(this.getUTCMinutes())   + ':' +
      f(this.getUTCSeconds())   + 'Z';
  };
  __dontenum__(Date.prototype, "toISOString");
}


// JSON.parse && JSON.stringify
if (typeof JSON === "undefined" ||
    (typeof JSON.parse !== "function" && typeof JSON.stringify !== "function")) {
  esxx.include("../json2.js");
  __dontenum__(this, "JSON");
  __dontenum__(Boolean.prototype, "toJSON");
  __dontenum__(Date.prototype, "toJSON");
  __dontenum__(Number.prototype, "toJSON");
  __dontenum__(String.prototype, "toJSON");
}


// Object.getPrototypeOf
if (typeof Object.getPrototypeOf !== "function"){
  Object.getPrototypeOf = function(obj) { return obj.__proto__; };
  __dontenum__(Object, "getPrototypeOf");
}

// Object.getOwnPropertyDescriptor ( O, P )
// Object.getOwnPropertyNames ( O )
// Object.create ( O [, Properties] )
// Object.defineProperty ( O, P, Attributes )
// Object.defineProperties ( O, Properties )
// Object.seal ( O )
// Object.freeze ( O )
// Object.preventExtensions ( O )
// Object.isSealed ( O )
// Object.isFrozen ( O )
// Object.isExtensible ( O )
// Object.keys ( O )

// String.prototype.trim
if (typeof String.prototype.trim !== "function") {
  String.prototype.trim = function () {
    return "" + new java.lang.String(this.toString()).trim();
  };
  __dontenum__(String.prototype, "trim");
}

delete __dontenum__;
