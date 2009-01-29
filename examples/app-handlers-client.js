#!/usr/bin/env esxx-js

var out = java.lang.System.out;

function main() {
  let stock = new URI("http://localhost:7777/examples/app-handlers.esxx/soap");
  
  let res = stock.query("POST", { SOAPAction: "urn:xmethods-delayed-quotes" },
			<SOAP-ENV:Envelope 
 			 SOAP-ENV:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"
			 xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
			 xmlns:xsi="http://www.w3.org/1999/XMLSchema-instance">
			  <SOAP-ENV:Body>
			    <ns1:getQuote xmlns:ns1="urn:xmethods-delayed-quotes">
			      <symbol xsi:type="xsd:string">IBM</symbol>
			    </ns1:getQuote>
			  </SOAP-ENV:Body>
			</SOAP-ENV:Envelope>,
			"text/xml");

  if (res.status == 200) {
    out.println(res.data.toXMLString());
  }

  return 0;
}
