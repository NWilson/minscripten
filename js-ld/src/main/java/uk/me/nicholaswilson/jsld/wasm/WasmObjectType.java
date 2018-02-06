package uk.me.nicholaswilson.jsld.wasm;

import uk.me.nicholaswilson.jsld.LdException;

public enum WasmObjectType {
  FUNCTION(0x00),
  TABLE(0x01),
  MEMORY(0x02),
  GLOBAL(0x03);

  public final int code;

  WasmObjectType(int code) {
    this.code = code;
  }

  public static WasmObjectType of(byte code) {
    for (WasmObjectType type : values())
      if (type.code == code)
        return type;
    throw new LdException("Invalid Wasm file: bad object type");
  }
}
