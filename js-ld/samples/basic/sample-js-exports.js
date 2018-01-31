import { wasm_add_one as wasm_add_one_budged } from '__symbols';

function getAddedBudged() {
  return wasm_add_one_budged();
}
export { getAddedBudged as getAdded };
