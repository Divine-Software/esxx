#!/bin/bash

RPM_SRV=martin@blom.org
RPM_DIR=/opt/Media/esxx.org/repos/rpm/

DEB_SRV=martin@blom.org
DEB_DIR=/opt/Media/esxx.org/repos/deb/

IPS_SSH="ssh -p 22022 lcs@blom.org"
IPS_HST=http://localhost:9999

set -e

if [ $# -ne 1 ]; then
    echo "Usage: $0 <package>"
    exit 10
fi

pkg_file=$1

case ${pkg_file} in
    *.dmg)
	echo "Cannot publish OSX packages."
	exit 20
	;;

    *.ips.tgz)
	${IPS_SSH} "pkgimport.sh" < ${pkg_file}
	;;

    *.rpm)
	scp ${pkg_file} ${RPM_SRV}:${RPM_DIR}/RPMS/
	ssh ${RPM_SRV} "createrepo ${RPM_DIR}"
	;;

    *.deb)
	scp ${pkg_file} ${DEB_SRV}:${DEB_DIR}/binary/
	ssh ${DEB_SRV} "cd ${DEB_DIR} && dpkg-scanpackages -m binary | gzip -9c > binary/Packages.gz"
	;;

    *)
	echo "Unsupported file extension in ${pkg_file}"
	exit 20
	;;
esac
