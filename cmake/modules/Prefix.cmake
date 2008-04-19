# Sets GNU-like prefices for install directories.

SET (LIB_SUFFIX "" CACHE STRING "Suffix for library directory (eg. '64')")
MARK_AS_ADVANCED(LIB_SUFFIX)

SET(EXEC_INSTALL_PREFIX "${CMAKE_INSTALL_PREFIX}" CACHE STRING 
			"Install location for architecture-dependent files.")
MARK_AS_ADVANCED(EXEC_INSTALL_PREFIX)

SET(BIN_INSTALL_DIR "${EXEC_INSTALL_PREFIX}/bin" CACHE STRING 
		    "Install location for user executables.")
SET(SBIN_INSTALL_DIR "${EXEC_INSTALL_PREFIX}/sbin" CACHE STRING 
		     "Install location for system admin executables.")
SET(LIBEXEC_INSTALL_PREFIX "${EXEC_INSTALL_PREFIX}/libexec" CACHE STRING 
		    	   "Install prefix for program executables.")

SET(LIB_INSTALL_DIR "${EXEC_INSTALL_PREFIX}/lib${LIB_SUFFIX}" CACHE STRING 
		    "Install location for libraries.")
SET(INCLUDE_INSTALL_DIR "${CMAKE_INSTALL_PREFIX}/include" CACHE STRING 
			"Install location for header files.")

SET(SYSCONF_INSTALL_DIR "${CMAKE_INSTALL_PREFIX}/etc" CACHE STRING 
			"Install location for read-only single-machine data..")
SET(SHARE_INSTALL_PREFIX "${CMAKE_INSTALL_PREFIX}/share" CACHE STRING 
		     	"Install location for read-only architecture-independent data.")
SET(SHAREDSTATE_INSTALL_PREFIX "${CMAKE_INSTALL_PREFIX}/com" CACHE STRING 
			       "Install location for modifiable architecture-independent data.")
SET(LOCALSTATE_INSTALL_PREFIX "${CMAKE_INSTALL_PREFIX}/var" CACHE STRING 
			      "Install location for modifiable single-machine data.")
SET(INFO_INSTALL_DIR "${CMAKE_INSTALL_PREFIX}/info" CACHE STRING 
		     "Install location for info documentation.")
SET(MAN_INSTALL_DIR "${CMAKE_INSTALL_PREFIX}/man" CACHE STRING 
		    "Install location for man documentation.")

#MARK_AS_ADVANCED(BIN_INSTALL_DIR)
#MARK_AS_ADVANCED(SBIN_INSTALL_DIR)
#MARK_AS_ADVANCED(LIBEXEC_INSTALL_PREFIX)
#MARK_AS_ADVANCED(LIB_INSTALL_DIR)
#MARK_AS_ADVANCED(INCLUDE_INSTALL_DIR)
#MARK_AS_ADVANCED(SYSCONF_INSTALL_DIR)
#MARK_AS_ADVANCED(SHARE_INSTALL_PREFIX)
#MARK_AS_ADVANCED(SHAREDSTATE_INSTALL_PREFIX)
#MARK_AS_ADVANCED(LOCALSTATE_INSTALL_PREFIX)
#MARK_AS_ADVANCED(INFO_INSTALL_DIR)
#MARK_AS_ADVANCED(MAN_INSTALL_DIR)
