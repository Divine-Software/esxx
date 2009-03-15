#!/usr/bin/env esxx-js

function main() {
  let dns=new URI("dns://blom.org/blom.org");

  esxx.log.info(dns.load());

  return 0;
}
