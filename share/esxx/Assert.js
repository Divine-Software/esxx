
/* A small unit testing framework for ESXX. */

function Assert() {
}

function Assert.typeOf(arg) {
  let res = typeof arg;

  if (res == "object") {
    res = arg.constructor.name || res
  }

  return res;
}

function Assert.Failed(test, reason, comment) {
  this.name = "Failed";

  this.test = test;
  this.reason = reason;
  this.comment = comment || "";
}

function Assert.that(arg, comment) {
  if (!arg) {
    throw new Assert.Failed("Assert.that",
			    arg + " does not evaluate to true",
			    comment);
  }
}

function Assert.isTrue(arg, comment) {
  if (typeof arg != "boolean") {
    throw new Assert.Failed("Assert.isTrue",
			    arg + " is not a boolean",
			    comment);
  }

  if (arg !== true) {
    throw new Assert.Failed("Assert.isTrue",
			    arg + " is not true",
			    comment);
  }
}

function Assert.isFalse(arg, comment) {
  if (typeof arg != "boolean") {
    throw new Assert.Failed("Assert.isFalse",
			    arg + " is not a boolean",
			    comment);
  }

  if (arg !== false) {
    throw new Assert.Failed("Assert.isFalse",
			    arg + " is not false",
			    comment);
  }
}

function Assert.isNull(arg, comment) {
  if (arg !== null) {
    throw new Assert.Failed("Assert.isNull",
			    arg + " is not null",
			    comment);
  }
}

function Assert.isNotNull(arg, comment) {
  if (arg === null) {
    throw new Assert.Failed("Assert.isNotNull",
			    arg + " is null",
			    comment);
  }
}


function Assert.isUndefined(arg, comment) {
  if (typeof arg !=  "undefined") {
    throw new Assert.Failed("Assert.isUndefined",
			    arg + " is not undefined",
			    comment);
  }
}

function Assert.isNotUndefined(arg, comment) {
  if (typeof arg ==  "undefined") {
    throw new Assert.Failed("Assert.isNotUndefined",
			    arg + " is undefined",
			    comment);
  }
}

function Assert.isNan(arg, comment) {
  if (!isNan(arg)) {
    throw new Assert.Failed("Assert.isNan",
			    arg + " is not NaN",
			    comment);
  }
}

function Assert.isNotNan(arg, comment) {
  if (isNan(arg)) {
    throw new Assert.Failed("Assert.isNotNan",
			    arg + " is NaN",
			    comment);
  }
}

function Assert.areEqual(arg1, arg2, comment) {
  if (arg1 != arg2) {
    throw new Assert.Failed("Assert.areEqual",
			    arg1 + " (" + Assert.typeOf(arg1)
			    + ") != " + arg2 + " (" + Assert.typeOf(arg2) + ")",
			    comment);
  }
}

function Assert.areNotEqual(arg1, arg2, comment) {
  if (arg1 == arg2) {
    throw new Assert.Failed("Assert.areNotEqual",
			    arg1  + " (" + Assert.typeOf(arg1)
			    + ") == " + arg2 + " (" + Assert.typeOf(arg2) + ")",
			    comment);
  }
}

function Assert.areIdentical(arg1, arg2, comment) {
  if (arg1 !== arg2) {
    throw new Assert.Failed("Assert.areIdentical",
			    arg1 + " (" + Assert.typeOf(arg1)
			    + ") !== " + arg2 + " (" + Assert.typeOf(arg2) + ")",
			    comment);
  }
}

function Assert.areNotIdentical(arg1, arg2, comment) {
  if (arg1 === arg2) {
    throw new Assert.Failed("Assert.areNotIdentical",
			    arg1  + " (" + Assert.typeOf(arg1)
			    + ") === " + arg2 + " (" + Assert.typeOf(arg2) + ")",
			    comment);
  }
}

function Assert.fnThrows(fn, type, comment) {
  try {
    fn();
  }
  catch (ex) {
    if (type instanceof Function) {
      if (!type(ex)) {
	throw new Assert.Failed("Assert.throws",
				fn + " did not throw an exception that did not pass test " + type,
				comment);
      }
    }
    else if (type) {
      if (!(ex instanceof type)) {
	throw new Assert.Failed("Assert.throws",
				fn + " did not throw an instance of " + type,
				comment);
      }
    }

    return;
  }

  throw new Assert.Failed("Assert.throws",
			  fn + " did not throw an exception",
			  comment);
}

function Assert.fnNotThrows(fn, comment) {
  try {
    fn();
  }
  catch (ex) {
    throw new Assert.Failed("Assert.notThrows",
			    fn + " threw an exception (" + Assert.typeOf(ex) + ")",
			    comment);
  }
}
