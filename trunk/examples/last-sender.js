#!/usr/bin/env esxx-js

importPackage(javax.mail);
importClass(java.lang.System);

function main(prg, location, username, password) {
  if (arguments.length < 3) {
    System.err.println("Usage: " + prg + " <IMAP URI> <username> [<password>]");
    return 10;
  }

  password = password || System.console().readPassword("Password: ");

  var session = Session.getDefaultInstance(System.getProperties());
  var store   = session.getStore(new URLName(location));

  store.connect(username, password);

  var inbox   = store.getFolder("INBOX");
  inbox.open(Folder.READ_ONLY);

  var message = inbox.getMessage(inbox.getMessageCount());

  System.out.println("Last message was from " + message.getSender());
  return 0;
}
