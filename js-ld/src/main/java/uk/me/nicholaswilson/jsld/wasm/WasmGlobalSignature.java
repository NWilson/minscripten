package uk.me.nicholaswilson.jsld.wasm;

public class WasmGlobalSignature {
  public final WasmValType valType;
  public final boolean mut;

  public WasmGlobalSignature(WasmValType valType, boolean mut) {
    this.valType = valType;
    this.mut = mut;
  }
}
