
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

  let rc = true;

  let testcases = 0;
  let tc_passed = 0;

  function run_tc(tc) {
    out.println("Running testcase " + tc.name + ":")

    ++testcases;

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

    out.println(passed + " out of " + tests + " tests ("
		+ (100 * passed / tests) + "%) passed for testcase " + tc.name);
    out.println("");

    if (tests === passed) {
      ++tc_passed;
      return true;
    }
    else {
      rc = false;
      return false;
    }
  }

  for (let i in this.tests) {
    if (this.tests[i] instanceof TestCase) {
      run_tc(this.tests[i]);
    }
    else if (this.tests[i] instanceof TestSuite) {
      out.println("Running testsuite " + this.tests[i].name + ":")

      let tcs = this.tests[i].getTestCases();
      let trc = true;
      
      for (let t in tcs) {
	trc = trc && run_tc(tcs[t]);
      }

      out.println("Testsuite " + this.tests[i].name + (trc ? " PASSED" : " FAILED"));
    }
  }

  out.println("TestRunner " + (tc_passed === testcases ? "PASSED: " : "FAILED: ")
	      + tc_passed + " out of " + testcases + " testcases ("
	      + (100 * tc_passed / testcases) + "%) passed");

  return rc ? 0 : 10;
}
