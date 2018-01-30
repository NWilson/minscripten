package uk.me.nicholaswilson.jsld;

import java.util.*;

public class SymbolTable {

  final Map<String, Definition> symbols = new LinkedHashMap<>();

  public static final SymbolTable INSTANCE = new SymbolTable();


  public void addSymbol(Definition symbol) {
    String symbolName = symbol.getName();
    Definition existingSymbol = symbols.get(symbolName);
    if (existingSymbol != null) {
      throw new LdException("Symbol " + symbolName + " from " +
        symbol.getSource() + " already defined in " +
        existingSymbol.getSource());
    }
    symbols.put(symbolName, symbol);
  }

  public Set<String> symbolSet() {
    return Collections.unmodifiableSet(symbols.keySet());
  }


  public interface Definition {
    String getName();
    String getSource();
  }

  public static class JsDefinition implements Definition {
    public final String symbolName;
    public final SymbolsFile symbolsFile;

    public String getName() { return symbolName; }
    public String getSource() { return symbolsFile.fileName.toString(); }

    public JsDefinition(String name, SymbolsFile file) {
      symbolName = name;
      symbolsFile = file;
    }
  }

  /*
  public static class WasmDefinition implements Definition {
    public final String symbolName;
    public final WasmFile wasmFile;

    public String getName() { return symbolName; }
    public String getSource() { return wasmFile.fileName.toString(); }

    public WasmDefinition(String name) {
      symbolName = name;
    }
  }
  */

  private SymbolTable() {}

}
