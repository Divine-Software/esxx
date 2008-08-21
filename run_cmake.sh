#!/bin/bash

LIB_SUFFIX=

test -d /lib64 && LIB_SUFFIX=64

if [ "$OSTYPE" == "darwin9.0" ]; then
    export JAVA_HOME=/System/Library/Frameworks/JavaVM.framework/Versions/1.6/Home
fi

cmake -DCMAKE_BUILD_TYPE=RelWithDebInfo \
      -DLIB_SUFFIX=$LIB_SUFFIX \
      -DCMAKE_INSTALL_PREFIX=/usr \
      -DSYSCONF_INSTALL_DIR=/etc \
      -DLOCALSTATE_INSTALL_PREFIX=/var \
      -DSHAREDSTATE_INSTALL_PREFIX=/var \
    $(dirname $0)
