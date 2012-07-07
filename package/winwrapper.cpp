
#include <windows.h>
#include <stdio.h>

int main(int argc, char *argv[])
{
    char module[1024];
    GetModuleFileName(0, module, sizeof (module));
    module[sizeof (module) - 1] = 0;

    char* last_slash;

    for (last_slash = module + strlen(module); last_slash > module; --last_slash) {
        if (*last_slash == '/' || *last_slash == '\\') {
            break;
        }
    }

    if (last_slash == module) {
        printf("Expected at least on (back-) slash in module name %s.\n", module);
        return 10;
    }

    char cmd[1024];
    _snprintf(cmd, sizeof (cmd), "cscript /nologo \"%.*s%s\" \"%.*s\" \"%s\"", 
            (last_slash - module + 1), module, "esxx-launcher.js", 
            (last_slash - module + 1), module, last_slash + 1);
    cmd[sizeof (cmd) - 1] = 0;

    char* command_line = (char*) malloc(strlen(cmd) + 1 + strlen(GetCommandLine()));
    sprintf(command_line, "%s %s", cmd, GetCommandLine());

    STARTUPINFO si;
    PROCESS_INFORMATION pi;

    memset(&si, 0, sizeof (si));
    memset(&pi, 0, sizeof (pi));

    si.cb = sizeof(si);

    if(!CreateProcess( NULL, command_line, NULL, NULL, FALSE, 0, NULL, NULL, &si, &pi)) {
        printf( "CreateProcess failed with error code %d.\n", GetLastError() );
        return 10;
    }

    WaitForSingleObject(pi.hProcess, INFINITE);

    DWORD rc = -1;
    rc = GetExitCodeProcess(pi.hProcess, &rc);

    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);

    free(command_line);
    return rc;
}
