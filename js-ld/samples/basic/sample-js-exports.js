// Sample usage of exporting objects.  An "exports" file passed to js-ld will
// add items to the table of objects that's exported as the module.  Here, the
// "symbol" (in this one from the Wasm module) is given a JavaScript wrapper
// function which is then exported.  getAdded will be callable on the final
// module.  A wrapper can be used to effectively hide the Wasm module, for
// example translating between JS and Wasm strings.
import { wasm_add_one as wasm_add_one_budged } from '__symbols';

function getAddedBudged() {
  return wasm_add_one_budged();
}
export { getAddedBudged as getAdded };

// Sample usage of functions from an external library: just import what you
// want from it, then you can export or use it.
import { isArray as isArrayBudged } from 'jQuery';
export { isArrayBudged as isArrayExport };
