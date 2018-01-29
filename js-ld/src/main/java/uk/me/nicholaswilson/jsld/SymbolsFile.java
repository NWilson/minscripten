package uk.me.nicholaswilson.jsld;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.shapesecurity.shift.ast.ExportDeclaration;
import com.shapesecurity.shift.ast.ImportDeclaration;
import com.shapesecurity.shift.ast.ImportDeclarationExportDeclarationStatement;
import com.shapesecurity.shift.ast.Module;
import com.shapesecurity.shift.parser.JsError;
import com.shapesecurity.shift.parser.Parser;

public class SymbolsFile {

  final Path fileName;
  final Module module;
  final Map<String, ExportDeclaration> exports = new HashMap<>();

  public SymbolsFile(Path fileName) {
    try {
      this.fileName = fileName;
      module = Parser.parseModule(Util.pathToString(fileName));
    } catch (JsError e) {
      throw new LdException("Error parsing symbols file: " + e.getMessage(), e);
    }

    validateModule();
  }

  private void validateModule() {
    for (ImportDeclarationExportDeclarationStatement s : module.items) {
      if (s instanceof ImportDeclaration)
        throw new LdException("Symbols file cannot contain import: " + fileName);
      if (!(s instanceof ExportDeclaration))
        continue;

      // XXX put in the exports. Any of these invalid?
      // ExportDefault, ExportAllFrom, Export, ExportFrom
    }

    for (String e : exports.keySet()) {
      SymbolTable.INSTANCE.addSymbol(new SymbolTable.JsDefinition(e, this));
    }
  }

}
