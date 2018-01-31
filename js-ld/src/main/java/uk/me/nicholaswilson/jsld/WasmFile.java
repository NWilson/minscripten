package uk.me.nicholaswilson.jsld;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.shapesecurity.shift.ast.AssignmentExpression;
import com.shapesecurity.shift.ast.ComputedMemberExpression;
import com.shapesecurity.shift.ast.ExpressionStatement;
import com.shapesecurity.shift.ast.IdentifierExpression;
import com.shapesecurity.shift.ast.LiteralStringExpression;
import com.shapesecurity.shift.ast.Statement;

public class WasmFile {

  private final Path path;
  private final List<String> imports = new ArrayList<>();
  private final List<String> exports = new ArrayList<>();

  public Path getPath() {
    return path;
  }

  public WasmFile(Path path) {
    this.path = path;

    // TODO load file from disk
    // TODO parse imports + exports
    imports.add("js_get_int"); // XXX
    exports.add("wasm_add_one"); // XXX
    exports.add("wasm_get_const"); // XXX
    exports.add("wasm_unused_symbol"); // XXX

    // TODO check one memory import, no table imports
    // TODO ignore table exports, memory exports

    validateNames();

    for (String importName : imports) {
      SymbolTable.Symbol symbol = SymbolTable.INSTANCE.addUndefined(importName);
      symbol.markUsed();
    }
    for (String exportName : exports) {
      SymbolTable.INSTANCE.addDefined(
        exportName,
        new SymbolTable.WasmDefinition(this)
      );
    }
  }

  public void appendExports(
    List<Statement> statements,
    String exportsName
  ) {
    SymbolTable symbolTable = SymbolTable.INSTANCE;
    for (String e : exports) {
      if (symbolTable.getSymbol(e).isUnused())
        continue;
      // __symbols['<EXPORTED_NAME>'] = exports['<EXPORTED_NAME>']
      statements.add(new ExpressionStatement(
        new AssignmentExpression(
          new ComputedMemberExpression(
            new LiteralStringExpression(e),
            new IdentifierExpression(ModuleGenerator.SYMBOLS_VAR)
          ),
          new ComputedMemberExpression(
            new LiteralStringExpression(e),
            new IdentifierExpression(exportsName)
          )
        )
      ));
    }
  }


  private void validateNames() {
    Set<String> duplicateSet = new HashSet<>();
    List<String> duplicates = Stream.concat(imports.stream(), exports.stream())
      .filter(s -> !duplicateSet.add(s))
      .collect(Collectors.toList());
    if (!duplicates.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      for (String s : duplicates) {
        sb.append("\n  ").append(s);
      }
      // Could be that a symbol is named twice in the imports, or exported
      // twice, or imported+exported.
      throw new LdException(
        "Wasm file '" + path + "' contains duplicate symbols:" + sb
      );
    }
  }

}
