#!/usr/bin/env esxx-js

importPackage(Packages.javax.swing);
importPackage(Packages.java.awt.event);

var out = java.lang.System.out;

function main(args) {
  var frame  = new JFrame();
  var button = new JButton("Press me");
  var signal = {};

  button.addActionListener(function(ev) {
      out.println("Pressed " + ev + " in " + frame);
      esxx.notify(signal);
    });

  frame.add(button);
  frame.pack();
  frame.show();

  esxx.wait(signal, 10000);

  return 0;
}
