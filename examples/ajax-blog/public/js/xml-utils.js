function encodeXMLElement(str) {
  return str.toString().replace(/&/g, "&amp;").replace(/</g, "&lt;");
}

function encodeXMLAttribute(str) {
  return encodeXMLElement(str).replace(/"/g, "&quot;");
}

function innerXML(node) {
  var res = "";

  for (var i = 0; i < node.childNodes.length; ++i) {
    res += outerXML(node.childNodes.item(i));
  }

  return res;
}

function outerXML(node) {
  var ieAttrs = /hideFocus|contentEditable|disabled|tabIndex/;
  var i;
  var res = "";

  switch (node.nodeType) {
    case 1: // Element
      res = "<" + node.tagName.toLowerCase();

      for (i = 0; i < node.attributes.length; ++i) {
        res += outerXML(node.attributes.item(i));
      }

      if (node.hasChildNodes() || /script|link/.test(node.tagName)) {
        res += ">" + innerXML(node) + "</" + node.tagName.toLowerCase() + ">";
      }
      else {
        res += "/>";
      }
      break;

    case 2: { // Attribute
      var value = node.nodeValue;
      if (! ieAttrs.test(node.nodeName)
	  && value !== null
	  && value !== ""
	  && value !== false) {

	if (value === true) {
	  value = node.nodeName;
	}

        res = " " + node.nodeName + "=\"" + encodeXMLAttribute(value) + "\"";
      }
      break;
    }

    case 3: // Text
      res += encodeXMLElement(node.nodeValue);
      break;
  }

  return res;
}

var htmlToXML = function(str) {
  var h2xh = document.createElement("div");
  h2xh.innerHTML = str;
  return innerXML(h2xh);
};
