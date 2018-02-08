package uk.me.nicholaswilson.jsld.wasm;

public class WasmObjectDescriptor {

  public final WasmObjectType type;

  public static class Function extends WasmObjectDescriptor {
    public final WasmFunctionSignature signature;

    public Function(WasmFunctionSignature signature) {
      super(WasmObjectType.FUNCTION);
      this.signature = signature;
    }
  }

  public static class Table extends WasmObjectDescriptor {
    public final WasmTableSignature signature;

    public Table(WasmTableSignature signature) {
      super(WasmObjectType.TABLE);
      this.signature = signature;
    }
  }

  public static class Memory extends WasmObjectDescriptor {
    public final WasmMemorySignature signature;

    public Memory(WasmMemorySignature signature) {
      super(WasmObjectType.MEMORY);
      this.signature = signature;
    }
  }

  public static class Global extends WasmObjectDescriptor {
    public final WasmGlobalSignature signature;

    public Global(WasmGlobalSignature signature) {
      super(WasmObjectType.GLOBAL);
      this.signature = signature;
    }
  }


  private WasmObjectDescriptor(WasmObjectType type) {
    this.type = type;
  }

}
