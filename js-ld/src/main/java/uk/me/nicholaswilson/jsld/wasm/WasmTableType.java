package uk.me.nicholaswilson.jsld.wasm;

public class WasmTableType {
  public final WasmElemType elemType;
  public final WasmLimits limits;

  public WasmTableType(WasmElemType elemType, WasmLimits limits) {
    this.elemType = elemType;
    this.limits = limits;
  }
}
