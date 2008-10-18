
/** A helper method on all objects that returns a new function that
 *  invokes the 'func' argument as if it had been a real member on
 *  the object.
 *
 *  @param  func  The function to treat as a object member.
 *  @return       Whatever 'func' returns.
 *  @throw        Whatever 'func' throws.
 *
 *  Example usage:
 *  <pre>
esxx.include("esxx/Object.js");

with (JavaImporter(java.awt,
		   javax.swing)) {
  function MyFrame() {
    this.frame = new JFrame("Test");
    this.btn = new JButton("Click me");

    this.frame.add(this.btn, BorderLayout.CENTER);
    this.frame.locationRelativeTo = null;

    this.btn.addActionListener(this._(function(ev) {
					esxx.log.info("'" + this.btn.label
                                                      + "' got event " + ev.getClass().getName());
				      }));

    this.frame.pack();
    this.frame.show();
   }


  function main() {
    let frame = new MyFrame();

    for (let i in [1, 2, 3]) {
      esxx.log.info(i);
    }

    esxx.wait(this);
    return 0;
  }
}
</pre>
 */

function Object.prototype._(func) {
  let self = this;

  return function() {
    return func.apply(self, arguments);
  };
};

// Never enumerate extra properties in Object!

java.lang.Class.forName("org.mozilla.javascript.ScriptableObject")
  .getMethod("setAttributes", java.lang.String, java.lang.Integer.TYPE)
  .invoke(Object.prototype, "_",
	  new java.lang.Integer(org.mozilla.javascript.ScriptableObject.DONTENUM));
