/* Compile with: cl /EHsc /Os winwrapper.exe */

#include <algorithm>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>

#include <windows.h>

using namespace std;

namespace {
    char const* version = "2012-07-07";

    struct quote {
        quote(string& _res) : res(_res) {}

        void operator()(char c) {
            if (c == '"') {
                res += '\\';
            }
            
            res += c;
        }

        private:
            string& res;
    };
}

int main(int argc, char const* argv[])
{
    DWORD rc;
    char _module[1024];

    // Find directory and base-name of executable
    rc = GetModuleFileName(0, _module, sizeof (_module));

    if (rc == 0 || rc == sizeof (_module)) {
        cerr << "Failed to get module name." << endl;
        return 10;
    }

    string module(_module);
    size_t last_slash = module.find_last_of("/\\");

    if (last_slash == string::npos) {
        cerr << "Expected at least one (back-) slash in module name " << module << "." << endl;
        return 10;
    }

    string path = module.substr(0, last_slash + 1);
    string name;

    transform(module.begin() + last_slash + 1, 
              find(module.begin() + last_slash + 1, module.end(), '.'),
              back_inserter(name), 
              ::tolower);

    // Build various parts of the command line
    string         JAVA = "java";
    string      JVMARGS = "";
    string    ESXX_PATH = path + "share;" + path + "share\\site";
    vector<string> ARGS;
    vector<string> LIBS;

    if (name == "esxx-js") {
        JVMARGS += " -client";
    }

    if (getenv("JAVA_HOME") != NULL) {
        JAVA = getenv("JAVA_HOME") + string("\\bin\\java");
    }

    if (getenv("ESXX_PATH") != NULL) {
        ESXX_PATH = getenv("ESXX_PATH");
    }

    istringstream iss(ESXX_PATH);
    vector<string> lib_paths;
    for (string p; getline(iss, p, ';'); lib_paths.push_back(p + "\\lib\\"));

    for (int i = 0; i < lib_paths.size(); ++i) {
        WIN32_FIND_DATA ffd;
        HANDLE hFind = FindFirstFile((lib_paths[i] + "*.jar").c_str(), &ffd);

        if (hFind != INVALID_HANDLE_VALUE) {
            do {
                if ((ffd.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) == 0) {
                    LIBS.push_back(lib_paths[i] + ffd.cFileName);
                }
            }
            while (FindNextFile(hFind, &ffd));
            
            FindClose(hFind);
        }
    }

    bool copy_args = name == "esxx-js";
    for (int i = 1; i < argc; ++i) {
        string arg = argv[i];

        if (copy_args) {
            ARGS.push_back(arg);
        }
        else {
            if (arg == "/?") {
                arg = "-?";
            }

            if (arg == "-?" || arg == "--help") {
                cout << "Usage: " << argv[0] << " [OPTIONS...]" << endl;
                cout << "  -j,--jvmargs <JVM args>      Extra arguments for Java" << endl;
                cout << endl;
                ARGS.push_back(arg);
            }
            else if (arg == "--version") {
                cout << "WinWrapper version " << version << endl;
                cout << endl;
                ARGS.push_back(arg);
            }
            else if ((arg == "-j" || arg == "--jvmargs") && i < argc - 1) {
                JVMARGS = argv[i + i];
                ++i;
            }
            else if (arg.substr(0, 10) == "--jvmargs=") {
                JVMARGS = arg.substr(10);
            }
            else if (arg == "--") {
                copy_args = true;
            }
            else {
                ARGS.push_back(arg);
            }
        }
    }

    // Build final command line

    ostringstream command_line;

    command_line << '"' << JAVA << "\" " << JVMARGS 
                 << " \"-Desxx.app.include_path=" << ESXX_PATH << '"'
                 << " \"-Done-jar.class.path=";

    for (int i = 0; i < LIBS.size(); ++i) {
        command_line << (i != 0 ? "|" : "") << LIBS[i];
    }

    command_line << '"'
                 << " -jar \"" << path << "sbin\\esxx.jar\"";

    if (name == "esxx-js") {
        command_line << " --script --";
    }

    for (int i = 0; i < ARGS.size(); ++i) {
        string quoted;
        for_each(ARGS[i].begin(), ARGS[i].end(), quote(quoted));

        command_line << " \"" << quoted << '"';
    }

    //    cout << "Command line: " << command_line.str() << endl;

    // Launch ESXX
    STARTUPINFO si;
    PROCESS_INFORMATION pi;

    memset(&si, 0, sizeof (si));
    memset(&pi, 0, sizeof (pi));

    si.cb = sizeof(si);

    if(!CreateProcess( NULL, (LPSTR) command_line.str().c_str(), 
                       NULL, NULL, FALSE, 0, NULL, NULL, &si, &pi)) {
        cerr << "CreateProcess failed with error code " <<  GetLastError() << "." << endl;
        return 10;
    }

    WaitForSingleObject(pi.hProcess, INFINITE);

    rc = -1;
    GetExitCodeProcess(pi.hProcess, &rc);
        
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);

    return rc;
}
