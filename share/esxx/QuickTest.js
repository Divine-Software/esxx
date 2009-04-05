
esxx.include("Test.js");

function main() {
  let tr = new TestRunner();
  tr.add(new TestCase(esxx.global));
  return tr.run();
}
