%echo off

set SOURCE=%CD%
set BUILD=%TEMP%\esxx-build.%RANDOM%

mkdir "%BUILD%"
cd /d "%BUILD%"

call "%SOURCE%\run_cmake.bat"
nmake package
copy esxx*.exe "%SOURCE%"
copy esxx*.zip "%SOURCE%"

cd /d "%SOURCE%"
rd /s /q "%BUILD%

set SOURCE=
set BUILD=
