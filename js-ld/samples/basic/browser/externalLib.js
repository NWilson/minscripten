"use strict";
(function(factory) {
  const root = self;
  const define = root.define;
  factory = factory.bind(null, root);
  if (typeof define === "function" && define.amd)
    define("externalLib", [], factory);
  else
    root["externalLib"] = factory();
}(function(root) {
  // Our external library is a simple jQuery-lookalike to illustrate how you'd
  // call something like jQuery from js-ld code.
  function $(query) {
    return root.document.querySelectorAll(query);
  }
  $.isArray = Array.isArray;
  return $;
}));
