
esxx.include("Assert.js");

function main(prog) {
  let out = java.lang.System.out;
  let err = java.lang.System.err;
  let scope = esxx.global;

  for (let v in scope) {
    if (/^test.*/.test(v) && scope[v] instanceof Function) {
      out.print("Running " + v + "() ... ");

      try {
        scope[v]();
        out.println("OK");
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

  return 0;
}
