#!/usr/bin/env esxx-js

var err = java.lang.System.err;
var out = java.lang.System.out;

function main(prg, location) {
  if (!location) {
    err.println("Usage: " + prg + " <location URI>");
    return 10;
  }

  var uri  = new URI(location);
  var data = uri.load("text/plain");

  out.println(data);
  return 0;
}
