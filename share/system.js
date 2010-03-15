
// This is a pathetic "implementation" of CommonJS' System module, but
// at least it's enough to make the Module/1.0 test suite pass ...

exports.stdio = {
  print: function (s) {
    java.lang.System.out.println(s);
  }
};
