%echo off

set SOURCE=%CD%
set BUILD=%TEMP%\esxx-build.%RANDOM%

mkdir "%BUILD%"
cd /d "%BUILD%"

call ant -buildfile "%SOURCE%\build.xml" -Dbuild.dir="%BUILD%" -Dprefix=/ -Ddocdir=/doc -Ddatadir=/share -DDESTDIR="%BUILD%\root" generate-build-files install

"%WIX%bin\heat" dir root -var var.RootDir -cg ESXXFiles -gg -scom -sreg -sfrag -srd  -dr INSTALLLOCATION -o ESXXFiles.wxs
"%WIX%bin\candle" -dRootDir=root ESXX.wxs ESXXFiles.wxs
"%WIX%bin\light" -ext WixUIExtension -cultures:en-us ESXX.wixobj ESXXFiles.wixobj -o ESXX.msi

copy esxx*.msi "%SOURCE%"

cd /d "%SOURCE%"
rd /s /q "%BUILD%

set SOURCE=
set BUILD=
