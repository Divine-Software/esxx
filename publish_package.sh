#!/bin/bash

RPM_DIR=/opt/Media/esxx.org/repos/rpm/
DEB_DIR=/opt/Media/esxx.org/repos/deb/
IPS_SRV=http://localhost:9999

set -e

if [ $# -ne 1 ]; then
    echo "Usage: $0 <package>"
    exit 10
fi

pkg_file=$1

case $(uname) in
    Darwin)
	echo "Cannot publish OSX packages."
	exit 20
	;;

    SunOS)
	tmpdir=$(mktemp -d -t publish_package.XXXXXXXX)
	(pwd=${PWD} && cd ${tmpdir} && gzcat ${pwd}/${pkg_file} | tar xf -)
	basedir=${tmpdir}/$(ls ${tmpdir})
	fmri=$(head -1 ${basedir}/manifest | sed -r 's/# ([^,]+), client release .*/\1/')
	$(pkgsend -s ${IPS_SRV} open "${fmri}")
	pkgsend -s ${IPS_SRV} include -d ${basedir} ${basedir}/manifest
	pkgsend -s ${IPS_SRV} close
	rm -r ${tmpdir}
	;;

    Linux)
	if [ -n "$(which dpkg 2> /dev/null)" ]; then
	    if [ -d ${DEB_DIR}/binary ]; then
		cp ${pkg_file} ${DEB_DIR}/binary/
		dpkg-scanpackages ${DEB_DIR}/binary /dev/null | 
			gzip -9c > ${DEB_DIR}/binary/Packages.gz
	    else
		echo "${DEB_DIR}/binary not found!"
		exit 20
	    fi
	else
	    if [ -d ${RPM_DIR}/RPMS ]; then
		cp ${pkg_file} ${RPM_DIR}/RPMS/
		createrepo ${RPM_DIR}
	    else
		echo "${REPO_DIR}/rpm/RPMS not found!"
		exit 20
	    fi
	fi
	;;

    *)
	echo "Unsupported system."
	exit 20
	;;
esac
