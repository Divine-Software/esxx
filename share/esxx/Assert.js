
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
    throw new Assert.Failed("Assert.isTrue",
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

function Assert.isEqual(arg1, arg2, comment) {
  if (arg1 != arg2) {
    throw new Assert.Failed("Assert.isEqual",
			    arg1 + "(" + Assert.typeOf(arg1) 
			    + ") != " + arg2 + "(" + Assert.typeOf(arg2) + ")",
			    comment);
  }
}

function Assert.isNotEqual(arg1, arg2, comment) {
  if (arg1 == arg2) {
    throw new Assert.Failed("Assert.isNotEqual",
			    arg1  + "(" + Assert.typeOf(arg1) 
			    + ") == " + arg2 + "(" + Assert.typeOf(arg2) + ")",
			    comment);
  }
}

function Assert.isIdentical(arg1, arg2, comment) {
  if (arg1 !== arg2) {
    throw new Assert.Failed("Assert.isIdentical",
			    arg1 + "(" + Assert.typeOf(arg1) 
			    + ") !== " + arg2 + "(" + Assert.typeOf(arg2) + ")",
			    comment);
  }
}

function Assert.isNotIdentical(arg1, arg2, comment) {
  if (arg1 === arg2) {
    throw new Assert.Failed("Assert.isNotIdentical",
			    arg1  + "(" + Assert.typeOf(arg1) 
			    + ") === " + arg2 + "(" + Assert.typeOf(arg2) + ")",
			    comment);
  }
}
