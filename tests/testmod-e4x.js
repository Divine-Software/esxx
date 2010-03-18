
testRunner.add(
  new TestSuite(
    "testmod-e4x", [
      new TestCase({
	  name: "Global functions",

	  testFunctions:
	  function() {
	    Assert.that(typeof isXMLName == "function");
	    Assert.that(typeof Namespace == "function");
	    Assert.that(typeof QName == "function");
	    Assert.that(typeof XML == "function");
	    Assert.that(typeof XMLList == "function");
	  },

	  testIsXMLName:
	  function() {
	    Assert.isTrue(isXMLName("_name"));
	    Assert.isFalse(isXMLName("$name"));
	    Assert.isFalse(isXMLName("prefix:name"));
	  }
	}),

      new TestCase({
	  name: "Namespace",

	  testConstructor:
	  function() {
	    let empty1 = Namespace();
	    Assert.areIdentical(empty1.prefix, "");
	    Assert.areIdentical(empty1.uri, "");

	    let empty2 = new Namespace();
	    Assert.areIdentical(empty2.prefix, "");
	    Assert.areIdentical(empty2.uri, "");

	    let def1 = Namespace("urn:default");
	    Assert.isUndefined(def1.prefix);
	    Assert.areIdentical(def1.uri, "urn:default");

	    let def2 = new Namespace("urn:default");
	    Assert.isUndefined(def2.prefix);
	    Assert.areIdentical(def2.uri, "urn:default");

	    let pre1 = Namespace("pre", "urn:prefix");
	    Assert.areIdentical(pre1.prefix, "pre");
	    Assert.areIdentical(pre1.uri, "urn:prefix");

	    let pre2 = new Namespace("pre", "urn:prefix");
	    Assert.areIdentical(pre2.prefix, "pre");
	    Assert.areIdentical(pre2.uri, "urn:prefix");
	  },

	  testCopyConstructor:
	  function() {
	    let orig1 = new Namespace();
	    let orig2 = new Namespace("urn:default");
	    let orig3 = new Namespace("pre", "urn:prefix");

	    let same1 = Namespace(orig1);
	    Assert.areIdentical(same1, orig1);

	    let same2 = Namespace(orig2);
	    Assert.areIdentical(same2, orig2);

	    let same3 = Namespace(orig3);
	    Assert.areIdentical(same3, orig3);

	    let copy1 = new Namespace(orig1);
	    Assert.areNotIdentical(copy1, orig1);

	    let copy2 = new Namespace(orig2);
	    Assert.areNotIdentical(copy2, orig2);

	    let copy3 = new Namespace(orig3);
	    Assert.areNotIdentical(copy3, orig3);

	    let copy4 = Namespace("newpre", orig2);
	    Assert.areNotIdentical(copy4, orig2);

	    let copy5 = Namespace("newpre", orig3);
	    Assert.areNotIdentical(copy5, orig3);
	  },

	/* test */
	/*     let copy2 = Namespace("pre", empty); */
	/*     Assert.areNotIdentical(copy2, empty); */
	/*     Assert.areIdentical(copy2.prefix, "pre"); */
	/*     Assert.areIdentical(copy2.uri, ""); */
	/*   }, */
	}),

      new TestCase({
	  name: "XML",
	  testConstructor: 
	  function() {
	    let text1 = XML();
	    Assert.areIdentical(text1.nodeKind(), "text");
	    Assert.areIdentical(text1.toXMLString(), "");

	    let text2 = new XML();
	    Assert.areIdentical(text2.nodeKind(), "text");
	    Assert.areIdentical(text2.toXMLString(), "");
	  },

	  testCopyConstructor:
	  function() {
	    let orig1 = new XML();
	    let orig2 = new XML("some text");
	    let orig3 = new XML("<element/>");

	    let same1 = XML(orig1);
	    Assert.areIdentical(same1, orig1);

	    let same2 = XML(orig2);
	    Assert.areIdentical(same2, orig2);

	    let same3 = XML(orig3);
	    Assert.areIdentical(same3, orig3);

	    let copy1 = new XML(orig1);
	    Assert.areNotIdentical(copy1, orig1);

	    let copy2 = new XML(orig2);
	    Assert.areNotIdentical(copy2, orig2);

	    let copy3 = new XML(orig3);
	    Assert.areNotIdentical(copy3, orig3);
	  },

	  testDOMConstructor:
	  function() {
	    let dr = org.w3c.dom.bootstrap.DOMImplementationRegistry.newInstance();
	    let di = dr.getDOMImplementation("XML 3.0");
	    let d  = di.createDocument(null, "root", null);
	    let de  = d.getDocumentElement();

	    let doc_wrapper = XML(d);
	    doc_wrapper.@attr1 = "a1";
	    Assert.areIdentical(de.getAttribute('attr1'), "a1");

	    let root_wrapper = XML(de);
	    root_wrapper.@attr2 = "a2";
	    Assert.areIdentical(de.getAttribute('attr1'), "a1");
	    Assert.areIdentical(de.getAttribute('attr2'), "a2");

	    let copy = new XML(de);
	    copy.@attr3 = "a3";
	    Assert.areIdentical(de.getAttribute('attr1'), "a1");
	    Assert.areIdentical(de.getAttribute('attr2'), "a2");
	    Assert.areIdentical(de.getAttribute('attr3'), "");

	    Assert.areEqual(copy.@attr1, "a1");
	    Assert.areEqual(copy.@attr2, "a2");
	    Assert.areEqual(copy.@attr3, "a3");
	  },

	  testDOMLevel1:
	  function() {
	    let dr = org.w3c.dom.bootstrap.DOMImplementationRegistry.newInstance();
	    let di = dr.getDOMImplementation("XML 3.0");
	    let d  = di.createDocument(null, "root", null);
	    let de  = d.getDocumentElement();

	    de.appendChild(d.createElement("level1"));
	    de.appendChild(d.createElementNS(null, "level2"));

	    Assert.areIdentical(XML(d).level1.length(), 1);
	    Assert.areIdentical(XML(d).level2.length(), 1);
	  }
      })
    ])
);

