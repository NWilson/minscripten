package uk.me.nicholaswilson.jsld.wasm;

public class WasmMemorySignature {
  public final WasmLimits limits;

  public WasmMemorySignature(WasmLimits limits) {
    this.limits = limits;
  }
}
