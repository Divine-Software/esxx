#!/usr/bin/env esxx-js

importPackage(Packages.javax.swing);
importPackage(Packages.java.awt.event);

var out = java.lang.System.out;

function main(args) {
  var lock = new java.util.concurrent.locks.ReentrantLock();
  var cond = lock.newCondition();

  var frame = new JFrame();
  var button = new JButton("Press me");

  button.addActionListener(function(ev) {
      out.println("Pressed " + ev + " in " + frame);
      lock.lock();
      try {
	cond.signal();
      }
      finally {
	lock.unlock();
      }
    });

  frame.add(button);
  frame.pack();
  frame.show();

  lock.lock();
  try {
    cond.await();
  }
  finally {
    lock.unlock();
  }

  return 0;
}
