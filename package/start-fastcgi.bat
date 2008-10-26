%~d0
cd "%~dp0"
java -Desxx.app.include_path="%~dp0\share;%~dp0\share\site" -jar "%~dp0\esxx.jar" -b 7654
