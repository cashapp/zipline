(function (root, factory) {
  if (typeof define === 'function' && define.amd)
    define(['exports'], factory);
  else if (typeof exports === 'object')
    factory(module.exports);
  else
    root['demo'] = factory(typeof this['demo'] === 'undefined' ? {} : this['demo']);
}(this, function (_) {
  'use strict';
  function sayHello() {
    goBoom3();
  }
  function goBoom3() {
    goBoom2();
  }
  function goBoom2() {
    goBoom1();
  }
  function goBoom1() {
    throw Error('boom!');
  }
  _.sayHello = sayHello;
  return _;
}));
//# sourceMappingURL=demo.js.map
