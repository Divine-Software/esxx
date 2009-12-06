#!/usr/bin/env esxx-js

esxx.include("esxx/Test.js");

const testRunner = new TestRunner();

// Load testcase modules
for each (let file in new URI(esxx.location, ".").load().file.(/^testmod-.*\.js$/.test(name)).@uri) {
  esxx.include(file);
}

function main() {
  return testRunner.run();
}
