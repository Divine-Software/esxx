#!/bin/bash

set -e

#if [ $UID -ne 0 ]; then
#    echo "$0 must be executed as root"
#    exit 10
#fi

umask 022

SOURCE=$(cd $(dirname $0) && pwd)
BUILD=$(mktemp -d -t esxx-build.XXXXXX)

cd ${BUILD}

# Build package
case $(uname) in
    Darwin)
	export JAVA_HOME=/System/Library/Frameworks/JavaVM.framework/Versions/1.6/Home
    	packagemaker=/Developer/Applications/Utilities/PackageMaker.app/Contents/MacOS/PackageMaker

	# OSX uses a symlink for /etc and /var -- we must too
	mkdir -p root/private/{etc,var}
	ln -s private/etc root/etc
	ln -s private/var root/var

	ant -buildfile ${SOURCE}/build.xml -Dbuild.dir=${BUILD} \
	    -Dprefix=/usr -Dsysconfdir=/etc -Dlocalstatedir=/var -Dsharedstatedir=/var \
	    -DDESTDIR=${BUILD}/root generate-build-files install
	rm root/etc root/var

	# Fetch various version variables
	. version

	mkdir rsrc
	cp root/usr/share/doc/esxx/LICENSE.txt rsrc/License.txt
	cp root/usr/share/doc/esxx/README      rsrc/ReadMe.txt

	mkdir pkg
	cp package/osx-uninstall.sh "pkg/Uninstall ESXX.command"
	chmod 755 "pkg/Uninstall ESXX.command"

	$packagemaker -build \
	    -p pkg/${package_full_name}.pkg \
	    -f root \
	    -r rsrc \
	    -i package/packagemaker-info.plist \
	    -d package/packagemaker-descr.plist \
	    -ds

	rm -f ${SOURCE}/${package_full_name}.dmg

	hdiutil create -size 32m image.dmg -srcfolder pkg -format UDRW \
	    -volname "${package_full_name}"

	hdiutil convert image.dmg -format UDZO -imagekey zlib-level=9 \
	    -o ${SOURCE}/${package_full_name}.dmg

	hdiutil internet-enable -yes \
	    ${SOURCE}/${package_full_name}.dmg
	;;
    SunOS)
	ant -buildfile ${SOURCE}/build.xml -Dbuild.dir=${BUILD} \
	    -Dprefix=/usr -Dsysconfdir=/etc -Dconfdir=/etc/default -Dlocalstatedir=/var -Dsharedstatedir=/var \
	    -DDESTDIR=${BUILD}/root generate-build-files install

	# Fetch various version variables
	. version

	export PKG_REPO=http://localhost:11111

	# Start a private package server
	/usr/lib/pkg.depotd -d ${BUILD}/depot -p 11111 --set-property publisher.prefix=esxx.org &
	pid=$!
	sleep 2

	test -d /proc/$pid

	$(pkgsend open ${package_name}@${package_version}-${package_release})
	pkgsend add set name=description value="${package_summary}"
	pkgsend add set name=info.classification \
	    value="org.opensolaris.category.2008:Web Services/Application and Web Servers"

	pkgsend add license root/usr/share/doc/esxx/LICENSE.txt license=${package_license}
	pkgsend add depend type=require fmri=SUNWj6rt
	pkgsend add depend type=require fmri=SUNWbash
	pkgsend add depend type=require fmri=SUNWsudo

	# These  config files need special treatment
	(cd root && for file in etc/default/*; do
		pkgsend add file ${file} path=${file} preserve=renamenew \
		    mode=644 owner=root group=sys
	    done)
	rm root/etc/default/*

	# Add the remaining files
	pkgsend import root

	pkgsend close

	fmri=$(pkgrecv -s ${PKG_REPO} --newest | grep esxx)
	pkgrecv  -s ${PKG_REPO} -d ips --raw ${fmri}
	(cd ips && tar cfz \
	    ${SOURCE}/${package_full_name}.ips.tgz *)

	kill $pid
	sleep 1
	;;

    Linux)
	if [ -f /etc/debian_version ]; then
	    # Assume we're on Debian. Create the config files in ${SOURCE}/debian first.
	    cd ${SOURCE}
	    ant -Dprefix=/usr -Dsysconfdir=/etc -Dconfdir=/etc/default -Dlocalstatedir=/var -Dsharedstatedir=/var \
		generate-build-files

	    set +e
	    dpkg-buildpackage

	    cd ${SOURCE}
	    dh_clean
	    rm debian/control debian/changelog
	    rm -r builddir
	elif [ -f /etc/redhat-release ]; then
	    # Assume RedHat
	    ant -buildfile ${SOURCE}/build.xml -Dbuild.dir=${BUILD} \
		-Dprefix=/usr -Dsysconfdir=/etc -Dconfdir=/etc/sysconfig -Dlocalstatedir=/var -Dsharedstatedir=/var \
		generate-build-files

	    # Fetch various version variables
	    . version

	    mkdir -p rpmroot/{BUILD,SPECS,SRPMS,RPMS/noarch} ${package_full_name}
	    cp -r ${SOURCE}/* ${package_full_name}/
	    tar cfz ${package_full_name}.tar.gz esxx.spec ${package_full_name}
	    rpmbuild -tb --define "_topdir ${BUILD}/rpmroot" ${package_full_name}.tar.gz
	    cp ${BUILD}/rpmroot/RPMS/noarch/${package_full_name}-*.rpm ${SOURCE}
	else
	    echo "Unknown Linux variant"
	fi
	;;
esac

cd ${SOURCE}
rm -r ${BUILD}
