package uk.me.nicholaswilson.jsld;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SymbolTable {

  private final Map<String, Symbol> symbols = new LinkedHashMap<>();

  public static final SymbolTable INSTANCE = new SymbolTable();


  public Symbol addDefined(String symbolName, Definition definition) {
    Symbol symbol = symbols.get(symbolName);
    if (symbol == null) {
      symbol = new Symbol(symbolName);
      symbols.put(symbolName, symbol);
    }

    if (symbol.isDefined()) {
      throw new LdException(
        "Symbol " + symbolName + " from " + definition.getSource() +
          " already defined in " + symbol.getDefinition().getSource()
      );
    }

    symbol.setDefinition(definition);
    return symbol;
  }

  public Symbol addUndefined(String symbolName) {
    Symbol symbol = symbols.get(symbolName);
    if (symbol == null) {
      symbol = new Symbol(symbolName);
      symbols.put(symbolName, symbol);
    }
    return symbol;
  }

  public Set<String> symbolSet() {
    return Collections.unmodifiableSet(symbols.keySet());
  }

  public Symbol getSymbol(String symbolName) {
    Symbol symbol = symbols.get(symbolName);
    assert(symbol != null);
    return symbol;
  }

  public void reportUndefined() {
    List<Symbol> undefined = symbols.values().stream()
      .filter(Symbol::isUndefined)
      .collect(Collectors.toList());

    if (!undefined.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      for (Symbol s : undefined) {
        sb.append("\n  ").append(s.getSymbolName());
      }
      throw new LdException("Undefined symbols:" + sb);
    }
  }


  public static class Symbol {

    private final String symbolName;
    private boolean used = false;
    private Definition definition = null;

    public String getSymbolName() {
      return symbolName;
    }

    public boolean isUsed() {
      return used;
    }

    public void markUsed() {
      used = true;
    }

    public Definition getDefinition() {
      return definition;
    }

    public void setDefinition(Definition definition) {
      this.definition = definition;
    }

    public boolean isDefined() {
      return definition != null;
    }

    public boolean isUndefined() {
      return definition == null;
    }

    protected Symbol(String symbolName) {
      this.symbolName = symbolName;
    }

  }

  public interface Definition {

    String getSource();

  }

  public static class JsDefinition implements Definition {

    public final SymbolsFile symbolsFile;

    public String getSource() {
      return symbolsFile.getPath().toString();
    }

    public JsDefinition(SymbolsFile file) {
      symbolsFile = file;
    }

  }

  public static class WasmDefinition implements Definition {

    public final WasmFile wasmFile;

    public String getSource() {
      return wasmFile.getPath().toString();
    }

    public WasmDefinition(WasmFile file) {
      wasmFile = file;
    }

  }


  private SymbolTable() {
  }

}
