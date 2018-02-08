package uk.me.nicholaswilson.jsld.wasm;

import java.util.Collections;
import java.util.List;

public class WasmFunctionSignature {
  public final List<WasmValType> parameterTypes;
  public final List<WasmValType> resultTypes;

  public WasmFunctionSignature(List<WasmValType> parameterTypes, List<WasmValType> resultTypes) {
    this.parameterTypes = Collections.unmodifiableList(parameterTypes);
    this.resultTypes = Collections.unmodifiableList(resultTypes);
  }
}
