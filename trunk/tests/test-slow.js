#!/usr/bin/env esxx-js

var out = java.lang.System.out;

var counter = 0;

function call(uri) {
  var cnt = ++counter;

  out.println("Sending request #" + cnt + " to " + uri);
  var result = uri.load();
  out.println("Got response #" + cnt + " from " + uri);

  return result;
}

function main(prog, url, count) {
  if (arguments.length < 2 || !url) {
    out.println("Usage: " + prog + " <url> [<count]>");
    return 10;
  }

  url   = new URI(url);
  count = count || 10;

  var tasks = [];
  for (var i = 0; i < count; ++i) {
    tasks.push(call);
  }

  var res = esxx.parallel(tasks, [url], -1, count);
  
  //out.println(res.join("\n"));

  return 0;
}
