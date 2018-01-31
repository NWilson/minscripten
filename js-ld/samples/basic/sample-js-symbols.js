function js_get_int_budged() {
  return window.parseInt(document.getElementById('theEdit').value);
}
export { js_get_int_budged as js_get_int };

import { wasm_get_const as wasm_get_const_budged } from '__symbols';
export function js_unused_symbol() {
  return wasm_get_const_budged();
}
