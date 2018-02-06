package uk.me.nicholaswilson.jsld.wasm;

import uk.me.nicholaswilson.jsld.LdException;

public enum WasmElemType {
  anyfunc(0x70);

  public final int code;

  WasmElemType(int code) {
    this.code = code;
  }

  public static WasmElemType of(byte code) {
    for (WasmElemType type : values())
      if (type.code == code)
        return type;
    throw new LdException("Invalid Wasm file: bad table elemtype");
  }
}
