#!/usr/bin/env esxx-js

importPackage(Packages.javax.swing);
importPackage(Packages.java.awt.event);

var out = java.lang.System.out;

var signal = {
  wait: esxx.sync(function() {
      var objClazz = java.lang.Class.forName('java.lang.Object');
      var waitMethod = objClazz.getMethod('wait', null);
      waitMethod.invoke(this, null);      
    }),

  notify: esxx.sync(function() {
     var objClazz = java.lang.Class.forName('java.lang.Object');
     var notifyMethod = objClazz.getMethod('notify', null);
     notifyMethod.invoke(this, null);   
    })
};

function main(args) {
  var frame = new JFrame();
  var button = new JButton("Press me");

  button.addActionListener(function(ev) {
      out.println("Pressed " + ev + " in " + frame);
      signal.notify();
    });

  frame.add(button);
  frame.pack();
  frame.show();

  signal.wait();

  return 0;
}
