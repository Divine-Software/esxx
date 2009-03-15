#!/usr/bin/env esxx-js

importPackage(Packages.javax.swing);
importPackage(Packages.java.awt.event);

var out = java.lang.System.out;

function main(cmd, title) {
  var frame  = new JFrame(title || "No title");
  var button = new JButton("Press me within 10 seconds");
  var signal = {};

  button.addActionListener(function(ev) {
      // This JS code is executed in the Swing thread
      out.println("Pressed " + ev + " in " + frame);
      esxx.notify(signal);
    });

  frame.setLocationRelativeTo(null);
  frame.add(button);
  frame.pack();
  frame.show();

  esxx.wait(signal, 10);

  return 0;
}
