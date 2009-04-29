CMAKE_MINIMUM_REQUIRED (VERSION 2.6)
PROJECT (esxx Java)
ENABLE_TESTING ()

SET (CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake/modules")

FIND_PACKAGE (Java REQUIRED)
FIND_PROGRAM (ANT ant REQUIRED)

SET (CPACK_SET_DESTDIR ON)
SET (CPACK_PACKAGE_NAME ${PROJECT_NAME})
SET (CPACK_PACKAGE_DESCRIPTION_FILE "${CMAKE_CURRENT_SOURCE_DIR}/README")
SET (CPACK_PACKAGE_DESCRIPTION_SUMMARY "ESXX - The friendly ECMAscript/XML Application Server")
SET (CPACK_PACKAGE_VENDOR "Divine Software")
SET (CPACK_PACKAGE_CONTACT "Martin Blom <martin@blom.org>")
SET (CPACK_RESOURCE_FILE_LICENSE "${CMAKE_CURRENT_SOURCE_DIR}/LICENSE.txt")
SET (CPACK_PACKAGE_VERSION_MAJOR "0")
SET (CPACK_PACKAGE_VERSION_MINOR "9")
SET (CPACK_PACKAGE_VERSION_PATCH "20090325b")
SET (CPACK_PACKAGE_INSTALL_DIRECTORY "ESXX")
SET (CPACK_IGNORE_FILES "/etc")

SET (PACKAGE_LICENSE "GPLv3")
SET (PACKAGE_DESCRIPTION "An easy-to-use, yet powerful, XML Application Server written in Java.")

IF (CMAKE_HOST_WIN32)
  # Plain ZIP package and Windows installer
  SET (CPACK_GENERATOR ZIP NSIS)
  SET (CPACK_NSIS_DISPLAY_NAME ${CPACK_PACKAGE_DESCRIPTION_SUMMARY})
  SET (CPACK_NSIS_HELP_LINK "http://esxx.org")
  SET (CPACK_NSIS_URL_INFO_ABOUT "http://esxx.org")
  SET (CPACK_NSIS_CONTACT "martin@blom.org")
  SET (CPACK_NSIS_MODIFY_PATH ON)
  SET(CPACK_NSIS_EXTRA_INSTALL_COMMANDS "
	CreateShortCut \\\"$SMPROGRAMS\\\\$STARTMENU_FOLDER\\\\Start HTTP Server.lnk\\\" \\\"$INSTDIR\\\\start-http.bat\\\"
	CreateShortCut \\\"$SMPROGRAMS\\\\$STARTMENU_FOLDER\\\\Start FastCGI Server.lnk\\\" \\\"$INSTDIR\\\\start-fastcgi.bat\\\"
")
  SET(CPACK_NSIS_EXTRA_UNINSTALL_COMMANDS "
	!insertmacro MUI_STARTMENU_GETFOLDER Application $MUI_TEMP
	Delete \\\"$SMPROGRAMS\\\\$MUI_TEMP\\\\Batch File.lnk\\\"
	Delete \\\"$SMPROGRAMS\\\\$MUI_TEMP\\\\Project ReadMe.lnk\\\"
")
ELSEIF (CMAKE_HOST_APPLE)
  SET (CONFDIR defaults)
  SET (CPACK_GENERATOR TGZ PackageMaker)
  CONFIGURE_FILE(package/packagemaker-descr.plist.in
                 ${CMAKE_CURRENT_BINARY_DIR}/package/packagemaker-descr.plist @ONLY)
  CONFIGURE_FILE(package/packagemaker-info.plist.in
                 ${CMAKE_CURRENT_BINARY_DIR}/package/packagemaker-info.plist @ONLY)
ELSEIF (CMAKE_SYSTEM_NAME STREQUAL "SunOS")
  SET (CONFDIR default)
  SET (CPACK_GENERATOR TGZ)
ELSEIF (CMAKE_SYSTEM_NAME STREQUAL "Linux")
  FIND_PROGRAM (RPM rpmbuild)
  FIND_PROGRAM (DPKG dpkg-deb)

  IF (RPM AND NOT DPKG)
    # RedHat-style package
    SET (CONFDIR sysconfig)
    SET (CPACK_GENERATOR RPM)
    SET (CPACK_RPM_PACKAGE_ARCHITECTURE "noarch")
    SET (CPACK_RPM_PACKAGE_RELEASE "1")
    SET (CPACK_RPM_PACKAGE_LICENSE ${PACKAGE_LICENSE})
    SET (CPACK_RPM_PACKAGE_GROUP "System Environment/Daemons")
    SET (CPACK_RPM_USER_BINARY_SPECFILE "${CMAKE_CURRENT_SOURCE_DIR}/package/esxx.spec.in")

    CONFIGURE_FILE(package/esxx.spec.in
                   ${CMAKE_CURRENT_BINARY_DIR}/esxx.spec @ONLY)
  ELSEIF (DPKG)
    # Debian-style package
    SET (CONFDIR default)
#    SET (CPACK_GENERATOR DEB)
#    SET (CPACK_DEBIAN_PACKAGE_ARCHITECTURE all)
#    SET (CPACK_DEBIAN_PACKAGE_SECTION web)
    CONFIGURE_FILE(debian/control.in 
                   ${CMAKE_CURRENT_SOURCE_DIR}/debian/control @ONLY)
    CONFIGURE_FILE(debian/changelog.in 
                   ${CMAKE_CURRENT_SOURCE_DIR}/debian/changelog @ONLY)
  ENDIF (RPM AND NOT DPKG)
ELSE (CMAKE_HOST_WIN32)
  # Generic package if unknown system
  SET (CPACK_GENERATOR ZIP)
ENDIF (CMAKE_HOST_WIN32)


INCLUDE (Prefix)
INCLUDE (CPack)

# Build using ANT
ADD_CUSTOM_TARGET (dist ALL COMMAND 
		   ${ANT} -buildfile ${CMAKE_CURRENT_SOURCE_DIR}/build.xml 
		          -Dbuild.dir=${CMAKE_CURRENT_BINARY_DIR}/build
		          -Ddist.dir=${CMAKE_CURRENT_BINARY_DIR}/dist
			  clean dist)
ADD_CUSTOM_TARGET (antclean COMMAND
		   ${ANT} -buildfile ${CMAKE_CURRENT_SOURCE_DIR}/build.xml
		          -Dbuild.dir=${CMAKE_CURRENT_BINARY_DIR}/build
		          -Ddist.dir=${CMAKE_CURRENT_BINARY_DIR}/dist
		    	   clean)

IF (UNIX AND NOT WIN32)
  CONFIGURE_FILE(package/version.in
		 ${CMAKE_CURRENT_BINARY_DIR}/package/version 
		 ESCAPE_QUOTES @ONLY)

  # Create and install binaries
  CONFIGURE_FILE(package/javawrapper.in ${CMAKE_CURRENT_BINARY_DIR}/@PROJECT_NAME@ @ONLY)
  CONFIGURE_FILE(package/jswrapper.in ${CMAKE_CURRENT_BINARY_DIR}/@PROJECT_NAME@-js @ONLY)

  INSTALL (PROGRAMS ${CMAKE_CURRENT_BINARY_DIR}/${PROJECT_NAME}-js
           DESTINATION ${BIN_INSTALL_DIR})

  INSTALL (PROGRAMS ${CMAKE_CURRENT_BINARY_DIR}/${PROJECT_NAME}
	  	    ${CMAKE_CURRENT_BINARY_DIR}/dist/esxx.jar 
           DESTINATION ${SBIN_INSTALL_DIR})

  # Create and install init scripts
  IF (CMAKE_HOST_APPLE)
    CONFIGURE_FILE(package/org.esxx.ESXX.plist.in 
                   ${CMAKE_CURRENT_BINARY_DIR}/package/org.esxx.ESXX.plist @ONLY)

    INSTALL (PROGRAMS ${CMAKE_CURRENT_BINARY_DIR}/package/org.esxx.ESXX.plist
             DESTINATION ${CMAKE_INSTALL_DIR}/Library/LaunchDaemons)
  ELSEIF (CMAKE_SYSTEM_NAME STREQUAL "SunOS")
    CONFIGURE_FILE(package/svc-method.in
                   ${CMAKE_CURRENT_BINARY_DIR}/package/svc-method @ONLY)
    CONFIGURE_FILE(package/svc-manifest.xml.in
                   ${CMAKE_CURRENT_BINARY_DIR}/package/svc-manifest.xml @ONLY)

    INSTALL (PROGRAMS ${CMAKE_CURRENT_BINARY_DIR}/package/svc-method
             DESTINATION /lib/svc/method
	     RENAME @PROJECT_NAME@)

    INSTALL (FILES ${CMAKE_CURRENT_BINARY_DIR}/package/svc-manifest.xml
             DESTINATION /var/svc/manifest/network
	     RENAME @PROJECT_NAME@.xml)

  ELSEIF (CMAKE_SYSTEM_NAME STREQUAL "Linux")
    CONFIGURE_FILE(package/initscript-esxx.in 
                   ${CMAKE_CURRENT_BINARY_DIR}/package/@PROJECT_NAME@ @ONLY)
    CONFIGURE_FILE(package/initscript-binfmt.in 
                   ${CMAKE_CURRENT_BINARY_DIR}/package/@PROJECT_NAME@-binfmt @ONLY)

    INSTALL (PROGRAMS ${CMAKE_CURRENT_BINARY_DIR}/package/@PROJECT_NAME@
		      ${CMAKE_CURRENT_BINARY_DIR}/package/@PROJECT_NAME@-binfmt
             DESTINATION ${SYSCONF_INSTALL_DIR}/init.d)
  ENDIF (CMAKE_HOST_APPLE)

  # Create and install config file
  IF (CMAKE_HOST_APPLE)
    CONFIGURE_FILE(package/config.osx.in ${CMAKE_CURRENT_BINARY_DIR}/package/@PROJECT_NAME@.conf @ONLY)
  ELSE (CMAKE_HOST_APPLE)
    CONFIGURE_FILE(package/config.in 
		   ${CMAKE_CURRENT_BINARY_DIR}/package/@PROJECT_NAME@.conf @ONLY)
  ENDIF (CMAKE_HOST_APPLE)

  INSTALL (FILES ${CMAKE_CURRENT_BINARY_DIR}/package/@PROJECT_NAME@.conf
	   DESTINATION ${SYSCONF_INSTALL_DIR}/${CONFDIR}
	   RENAME @PROJECT_NAME@)

  INSTALL (FILES ${CMAKE_CURRENT_BINARY_DIR}/package/@PROJECT_NAME@.conf
	   DESTINATION ${SYSCONF_INSTALL_DIR}/${CONFDIR}
	   RENAME @PROJECT_NAME@-js)

  # Install utility files
  INSTALL (DIRECTORY share/
	   DESTINATION ${SHARE_INSTALL_PREFIX}/${PROJECT_NAME}/
	   PATTERN ".svn" EXCLUDE)

  # Install examples
  INSTALL (DIRECTORY examples
	   DESTINATION ${SHARE_INSTALL_PREFIX}/doc/${PROJECT_NAME}/
	   PATTERN .svn EXCLUDE
	   PATTERN examples/*.js 
	   PERMISSIONS OWNER_EXECUTE OWNER_WRITE OWNER_READ
	   	       GROUP_EXECUTE GROUP_READ
		       WORLD_EXECUTE WORLD_READ)

  # Install licences, changelog and readme files
  INSTALL (FILES CHANGES.txt
	  	 LICENSE.txt
	  	 LICENSE.APL2
	         LICENSE.BSD
	         LICENSE.GPL-3
	         LICENSE.HtmlCleaner
	         LICENSE.JavaMail
		 LICENSE.MPL-1.0
		 LICENSE.MPL-1.1
		 README
	   DESTINATION ${SHARE_INSTALL_PREFIX}/doc/${PROJECT_NAME}/)
ELSEIF (WIN32)
  INSTALL (PROGRAMS ${CMAKE_CURRENT_SOURCE_DIR}/package/start-http.bat
                    ${CMAKE_CURRENT_SOURCE_DIR}/package/start-fastcgi.bat
                    ${CMAKE_CURRENT_SOURCE_DIR}/package/esxx.bat
                    ${CMAKE_CURRENT_SOURCE_DIR}/package/esxx-js.bat
           DESTINATION ${CMAKE_INSTALL_PREFIX})
  INSTALL (DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}/dist/
	       DESTINATION ${CMAKE_INSTALL_PREFIX})
ENDIF (UNIX AND NOT WIN32)
