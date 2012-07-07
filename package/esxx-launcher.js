
if (WScript.FullName.toLowerCase().indexOf('cscript.exe') == -1) {
	WScript.Echo(WScript.ScriptName + ' should only be invoked using cscript.exe.');
	WScript.Quit(10);
}

if (WScript.Arguments.Count() < 3) {
	WScript.Echo('Required arguments missing.');
	WScript.Quit(10);
}

var root   = WScript.Arguments.Item(0);
var module = WScript.Arguments.Item(1).replace(/\.[^.]*$/, '');
var prog   = WScript.Arguments.Item(2);

var fso    = new ActiveXObject('Scripting.FileSystemObject');
var shell  = new ActiveXObject('WScript.Shell');
var env    = shell.Environment;

var JAVA      = env.Item('JAVA_HOME') ? env.Item('JAVA_HOME') + '\\bin\\java' : 'java';
var JVMARGS   = module == 'esxx-js' ? '-client' : '';
var ESXX_PATH = env.Item('ESXX_PATH') || (root + 'share;' + root + 'share\\site');
var ARGS      = [];
var LIBS      = [];

var i, e, arg, path, rc;

path = ESXX_PATH.split(';');
for (i in path) {
	if (fso.FolderExists(path[i] + '\\lib')) {
		for (e = new Enumerator(fso.GetFolder(path[i] + '\\lib').files); !e.atEnd(); e.moveNext()) {
			if (/.*\.jar$/.test(e.item())) {
				LIBS.push(e.item());
			}
		}
	}
}

LIBS = LIBS.join('|');

if (fso.FileExists(root + 'etc\\' + module + '.js')) {
	eval(fso.OpenTextFile(root + 'etc\\' + module + '.js', 1).ReadAll());
}

if (module != 'esxx' && fso.FileExists(root + 'etc\\esxx.js')) {
	eval(fso.OpenTextFile(root + 'etc\\esxx.js', 1).ReadAll());
}

for (i = 3; i < WScript.Arguments.Count(); ++i) {
	arg = WScript.Arguments.Item(i);

	if (module == 'esxx-js') {
		ARGS.push(arg);
	}
	else {
		if (arg == '/?' || arg == '-?' || arg == '--help') {
			WScript.Echo('Usage: ' + prog + ' [OPTIONS...]');
			WScript.Echo('  -j, --jvmargs=<JVM args>              Extra arguments for Java');
			WScript.Echo('');
			ARGS.push(arg);
		}
		else if ((arg == '-j' || arg == '--jvmargs') && i <  WScript.Arguments.Count() - 1) {
			JVMARGS = WScript.Arguments.Item(i + 1);
			++i;
		}
		else if (/^--jvmargs=.*/.test(arg)) {
			JVMARGS = arg.substring(10);
		}
		else if (arg == '--') {
			for (i = i + 1; i < WScript.Arguments.Count(); ++i) {
				ARGS.push(WScript.Arguments.Item(i));
			}
		}
		else {
			ARGS.push(arg);
		}
	}
}

for (i in ARGS) {
	ARGS[i] = '"' + ARGS[i] + '"';
}
ARGS = ARGS.join(' ');

function run(cmd) {
	return shell.Run(cmd, 10, true);
}

if (module == 'esxx-js') {
	rc = run(JAVA + ' ' + JVMARGS + 
	        ' "-Desxx.app.include_path=' + ESXX_PATH + '"' +
	        ' "-Done-jar.class.path=' + LIBS + '"' +
	        ' -jar "' + root + 'sbin\\esxx.jar" --script -- ' + ARGS);
}
else {
	rc = run(JAVA + ' ' + JVMARGS + 
	         ' "-Desxx.app.include_path=' + ESXX_PATH + '"' +
	         ' "-Done-jar.class.path=' + LIBS + '"' +
	         ' -jar "' + root + 'sbin\\esxx.jar" ' + ARGS);
}

WScript.Quit(rc);
