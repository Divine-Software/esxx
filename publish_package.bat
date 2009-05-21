@echo off

set COMMANDS=%TEMP%\esxx-publish.%RANDOM%

echo user ftp > %COMMANDS%
echo "" >> %COMMANDS%
echo bin >> %COMMANDS%
echo cd incoming >> %COMMANDS%

for %%f in (%*) do echo put %%f >> %COMMANDS%
echo quit >> %COMMANDS%

ftp -n -s:%COMMANDS% ftp.berlios.de

del %COMMANDS%
