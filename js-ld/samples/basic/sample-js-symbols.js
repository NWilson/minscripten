// Sample usage of a "symbols" file.  Here we are defining a symbol in
// JavaScript (using some helpers from an external module).  These will then be
// callable in the Wasm module.
import $ from 'externalLib';
function js_get_int_budged() {
  return Number.parseInt($('#theEdit')[0].value);
}
export { js_get_int_budged as js_get_int }

// Sample unused symbol.  Because it's not used, it will become eligible for
// dead-code-elimination.
import { wasm_get_const as wasm_get_const_budged } from '__symbols';
export function js_unused_symbol() {
  return wasm_get_const_budged();
}
