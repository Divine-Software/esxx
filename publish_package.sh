#!/bin/bash

set -e

if [ $# -ne 2 ]; then
    echo "Usage: $0 <package> <test|release>"
    exit 10
fi

pkg_file=$1
pkg_mode=$2

RPM_SRV=martin@blom.org
DEB_SRV=martin@blom.org
IPS_SRV=lcs@192.168.1.102

case ${pkg_mode} in
    test)
	RPM_DIR=/opt/Media/esxx.org/repos/rpm-testing/
	DEB_DIR=/opt/Media/esxx.org/repos/deb-testing/
	IPS_RPO=http://localhost:10010
	;;

    release)
	RPM_DIR=/opt/Media/esxx.org/repos/rpm/
	DEB_DIR=/opt/Media/esxx.org/repos/deb/
	IPS_RPO=http://localhost:10011
	;;

    *)
	echo "Unsupported mode: ${pkg_mode}"
	exit 20
	;;
esac
	

case ${pkg_file} in
    *.dmg)
	# FTP only
	;;

    *.ips.tgz)
	ssh ${IPS_SRV} "PKG_REPO=${IPS_RPO} /opt/pkgimport.sh -" < ${pkg_file}
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

if [ "${pkg_mode}" = "release" ]; then
    case $(uname) in 
	SunOS|Linux)
	    ncftpput ftp.berlios.de incoming/ ${pkg_file}
	    ;;

	Darwin)
	    ftp -u ftp://ftp.berlios.de/incoming/ ${pkg_file}
	    ;;

	*)
	    echo "Unsupported system: $(uname)"
	    exit 20
	    ;;
    esac
fi
