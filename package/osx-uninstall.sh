#!/bin/bash

set -e

if [ ${UID} -ne 0 ]; then
    echo "${0} must be executed as root"
    exit 10
fi

cd /

B="\033[1m"
N="\033[0m"
RECEIPTS=/Library/Receipts
SYSDIRS='^(\.|\./Library|\./Library/LaunchDaemons|\./etc|\./etc/default|\./private|\./private/etc|\./private/etc/defaults|\./usr|\./usr/bin|\./usr/sbin|\./usr/share|\./usr/share/doc|)$'

candidates=$(find ${RECEIPTS} -type d -name 'esxx*' -maxdepth 1)

echo -e "${B}The following installed ESXX packages were found in $RECEIPTS:${N}"
echo
cnt=1;
for c in ${candidates}; do
    echo "${cnt}: ${c}"
    ((++cnt))
done
echo -e -n "${B}Please select one to be uninstalled or 'q' to quit: ${N}"
read id

if [ "${id}" == "q" ]; then
    exit 5
fi

cnt=1;
for c in ${candidates}; do
    if ((cnt == id)); then
	candidate="${c}"
	break
    fi
    ((++cnt))
done

if [ -z "${candidate}" ]; then
    echo -e "${B}No package with ID ${id}${N}"
    exit 5
fi

echo -e "${B}${candidate} will be uninstalled.${N}"
files=$(lsbom -sflbc "${candidate}/Contents/Archive.bom")
dirs=$(lsbom -sd "${candidate}/Contents/Archive.bom" | grep -E -v "${SYSDIRS}" | sort -r)
echo

echo "${files}"
echo -e -n "${B}The files listed above will be removed. Press 'y' to proceed. ${N}"
read -n 1 rmfiles
echo

if [ "${rmfiles}" != "y" ]; then
    echo -e "${B} Aborted.${N}"
    exit 5
fi

set +e

# Remove files
rm ${files}

echo
echo "${dirs}"

echo -e -n "${B}The directories listed above will be removed, if non-empty. Press 'y' to proceed. ${N}"
read -n 1 rmdirs
echo

if [ "${rmdirs}" != "y" ]; then
    echo -e "${B} Aborted.${N}"
    exit 5
fi

# Remove (empty) directories
set +e
rmdir ${dirs}

# Remove receipt
rm -r "${candidate}"

echo -e "${B}ESXX removal completed.${N}"
