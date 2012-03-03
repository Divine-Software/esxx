#!/bin/bash

set -e

B="\033[1m"
N="\033[0m"
PKGID=org.esxx.ESXX
SYSDIRS='^(Library|Library/LaunchDaemons|etc|etc/default|private|private/etc|private/etc/defaults|private/var|usr|usr/bin|usr/sbin|usr/share|usr/share/doc|)$'

function quit {
    local rc="$1"
    local msg="$2"
    echo -e "${B}${msg}${N}"
    echo "Press any key to exit."
    read -s -n 1
    exit ${rc}
}

echo

if [ ${UID} -ne 0 ]; then
    echo -e -n "${B}${0} must be executed as root.\nPress 'y' to proceed using 'sudo'. ${N}"
    read -s -n 1 sume
    echo

    if [ "${sume}" != "y" ]; then
	quit 5 "Aborted."
    else
	exec sudo "${0}"
	quit 10 "sudo failed."
    fi
fi

cd /

if [ "$(pkgutil --pkgs=${PKGID})" != "${PKGID}" ]; then
    quit 5 "${PKGID} does not appear to be installed."
fi

echo -e "${B}${PKGID} will be uninstalled.${N}"
pkgutil --pkg-info ${PKGID}

files=$(pkgutil --only-files --files ${PKGID})
dirs=$(pkgutil --only-dirs --files ${PKGID} | grep -E -v "${SYSDIRS}" | sort -r)

echo
echo "${files}"
echo
echo -e -n "${B}The files listed above will be removed. Press 'y' to proceed. ${N}"
read -s -n 1 rmfiles
echo

if [ "${rmfiles}" != "y" ]; then
    quit 5 "Aborted."
fi

# Remove files
set +e
rm ${files}
set -e

echo
echo "${dirs}"
echo
echo -e -n "${B}The directories listed above will be removed, if non-empty. Press 'y' to proceed. ${N}"
read -s -n 1 rmdirs
echo

if [ "${rmdirs}" != "y" ]; then
    quit 5 "Aborted."
fi

# Remove (empty) directories
set +e
rmdir ${dirs}
set -e

# Remove receipt
echo
pkgutil --forget ${PKGID}
echo

quit 0 "ESXX removal completed."
