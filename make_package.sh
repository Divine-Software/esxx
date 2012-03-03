#!/bin/bash

set -e

#if [ $UID -ne 0 ]; then
#    echo "$0 must be executed as root"
#    exit 10
#fi

umask 022

SOURCE=$(cd $(dirname $0) && pwd)
BUILD=$(mktemp -d -t esxx-build.XXXXXX)
ANT="ant -buildfile ${SOURCE}/build.xml -Dbuild.dir=${BUILD}
         -Dprefix=/usr -Dsysconfdir=/etc -Dlocalstatedir=/var -Dsharedstatedir=/var"

cd ${BUILD}

# Configure
${ANT} generate-install-files

# Fetch various version variables
. package/version

# Build package
case $(uname) in
    Darwin)
	export JAVA_HOME=/System/Library/Frameworks/JavaVM.framework/Versions/1.6/Home
    	packagemaker=/Developer/Applications/Utilities/PackageMaker.app/Contents/MacOS/PackageMaker

	# OSX uses a symlink for /etc and /var -- we must too
	mkdir -p root/private/{etc,var}
	ln -s private/etc root/etc
	ln -s private/var root/var

	${ANT} -DDESTDIR=${BUILD}/root install
	rm root/etc root/var

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
	export PKG_REPO=http://localhost:11111

	# Start a private package server
	/usr/lib/pkg.depotd -d depot -p 11111 --set-property publisher.prefix=esxx.org &
	pid=$!
	sleep 2

	test -d /proc/$pid


	$(pkgsend open ${package_name}@${package_major}.${package_minor}.${package_patch}-1)
	pkgsend add set name=description value="${package_summary}"
	pkgsend add set name=info.classification \
	    value="org.opensolaris.category.2008:Web Services/Application and Web Servers"

	pkgsend add license ${package_license_file} license=${package_license}
	pkgsend add depend type=require fmri=SUNWj6rt
	pkgsend add depend type=require fmri=SUNWbash
	pkgsend add depend type=require fmri=SUNWsudo

	umask 0002
	make install DESTDIR=root
	chown -R root:sys root
	chown -R root:bin root/lib
	chown -R root:bin root/usr/bin
	chown -R root:bin root/usr/share/esxx
	chown -R root:bin root/usr/share/doc/esxx

	# These two config files need special treatment
	pkgsend add file root/etc/default/esxx path=etc/default/esxx preserve=renamenew \
	    mode=644 owner=root group=sys
	pkgsend add file root/etc/default/esxx-js path=etc/default/esxx-js preserve=renamenew \
	    mode=644 owner=root group=sys
	rm root/etc/default/esxx*

	# Add the remaining files
	(cd root && tar cf ../root.tar *)
	pkgsend import root.tar

	pkgsend close

	fmri=$(pkgrecv -s ${PKG_REPO} -n | grep esxx)
	pkgrecv  -s ${PKG_REPO} -d ips ${fmri}
	(cd ips && tar cfz \
	    ${SOURCE}/${package_full_name}.ips.tgz *)

	kill $pid
	sleep 1
	;;

    Linux)
	if [ -f /etc/debian_version ]; then
	    # Assume we're on Debian. Create the config files in ${SOURCE}/debian first.
	    cd ${SOURCE}
	    ${ANT} generate-build-files

	    set +e
	    dpkg-buildpackage

	    cd ${SOURCE}
	    dh_clean
	    rm debian/control debian/changelog
	    rm -r builddir
	else
	    mkdir -p rpmroot/{BUILD,SPECS,SRPMS,RPMS/noarch} ${package_full_name}
	    cp -r ${SOURCE}/* ${package_full_name}/
	    tar cfz ${package_full_name}.tar.gz ${package_full_name} esxx.spec
	    rpmbuild -tb --define "_topdir ${BUILD}/rpmroot" ${package_full_name}.tar.gz
	    cp ${BUILD}/rpmroot/RPMS/noarch/${package_full_name}-*.rpm ${SOURCE}
	fi
	;;
esac

cd ${SOURCE}
rm -r ${BUILD}
