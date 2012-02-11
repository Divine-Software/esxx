const out = java.lang.System.out;
const err = java.lang.System.err;

function main() {
  // Invoke ESXX shell
  new org.esxx.shell.Shell(org.mozilla.javascript.Context.getCurrentContext(), esxx.global).run();
  return 0;
}
