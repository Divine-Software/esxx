%echo off

set SOURCE=%CD%
set BUILD=%TEMP%\esxx-build.%RANDOM%

mkdir "%BUILD%"
cd /d "%BUILD%"

call ant -buildfile "%SOURCE%\build.xml" -Dbuild.dir="%BUILD%" -Dprefix=/ -Ddocdir=/doc -Ddatadir=/share -DDESTDIR="%BUILD%\root" generate-build-files install

call run_wix.bat
copy esxx*.msi "%SOURCE%"

cd /d "%SOURCE%"
rd /s /q "%BUILD%

set SOURCE=
set BUILD=
