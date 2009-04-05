
esxx.include("Assert.js");


function TestCase(props) {
  // Copy test.*, name, setUp, tearDown

  for (let i in props) {
    if (/^(test.*|name|setUp|tearDown)$/.test(i)) {
      this[i] = props[i];
    }
  }

  this.name = this.name || "<unnamed testcase>";
}


function TestSuite(name) {
  this.name = name;
  this.testCases = [];
}

function TestSuite.prototype.add(tc) {
  this.testCases.push(tc);
}

function TestSuite.prototype.getTestCases() {
  return this.testCases;
}


function TestRunner() {
  this.tests = [];
}

function TestRunner.prototype.add(t) {
  this.tests.push(t);
}

function TestRunner.prototype.clear() {
  this.tests = [];
}

function TestRunner.prototype.run() {
  let out = java.lang.System.out;
  let err = java.lang.System.err;

  function run_tc(tc) {
    out.println("Running testcase " + tc.name + ":")

    let tests  = 0;
    let passed = 0;

    for (let v in tc) {
      if (/^test.*/.test(v) && typeof tc[v] === "function") {
	out.print(" Running " + v + "() ... ");

	try {
	  ++tests;

	  if (typeof tc.setUp === "function") {
	    tc.setUp();
	  }

	  try {
            tc[v]();

            out.println("OK");
	    ++passed;
	  }
	  finally {
	    if (typeof tc.tearDown === "function") {
	      tc.tearDown();
	    }
	  }
	}
	catch (ex if ex instanceof Assert.Failed) {
          out.println("FAILED in " + ex.test);
          err.println(ex.reason);
          err.println(ex.comment);
	}
	catch (ex) {
          out.println("FAILED");
          err.println("Unknown exception caught: " + ex);
	}
      }
    }

    out.println(passed + " out of " + tests + " tests passed for testcase " + tc.name);

    return tests === passed;
  }

  let rc = true;

  for (let i in this.tests) {
    if (this.tests[i] instanceof TestCase) {
      rc = run_tc(this.tests[i]) && rc;
    }
    else if (this.tests[i] instanceof TestSuite) {
      let tcs = this.tests[i].getTestCases();
      
      for (let t in tcs) {
	rc = run_tc(tcs[t]) && rc;
      }
    }
  }

  return rc ? 0 : 10;
}
