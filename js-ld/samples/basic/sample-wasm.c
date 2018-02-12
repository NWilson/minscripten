int js_get_int(void);
int wasm_add_one(void) {
  return 1 + js_get_int();
}

int wasm_get_const(void) {
  return 999;
}

int wasm_unused_symbol(void) {
  return 0;
}
