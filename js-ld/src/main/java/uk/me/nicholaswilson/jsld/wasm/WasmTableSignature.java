package uk.me.nicholaswilson.jsld.wasm;

public class WasmTableSignature {
  public final WasmElemType elemType;
  public final WasmLimits limits;

  public WasmTableSignature(WasmElemType elemType, WasmLimits limits) {
    this.elemType = elemType;
    this.limits = limits;
  }
}
