package uk.me.nicholaswilson.jsld.wasm;

public class WasmGlobalType {
  public final WasmValType valType;
  public final boolean mut;

  public WasmGlobalType(WasmValType valType, boolean mut) {
    this.valType = valType;
    this.mut = mut;
  }
}
