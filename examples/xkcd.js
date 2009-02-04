#!/usr/bin/env esxx-js

with (JavaImporter(javax.swing)) {
  function main() {
    let atom    = new Namespace("http://www.w3.org/2005/Atom");

    let entry   = new URI("http://xkcd.com/atom.xml").load().atom::entry[0];
    let img_tag = new XML(entry.atom::summary.toString());
    let image   = new URI(img_tag.@src).load();

    let frame   = new JFrame(entry.atom::title);

    frame.defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE;
    frame.add(new JLabel(new ImageIcon(image)));
    frame.pack();
    frame.locationRelativeTo = null;
    frame.visible = true;
    esxx.wait(this); // Wait forever
  }
}
