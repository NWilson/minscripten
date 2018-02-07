package uk.me.nicholaswilson.jsld;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.functional.data.Maybe;
import com.shapesecurity.shift.ast.*;
import com.shapesecurity.shift.parser.JsError;
import com.shapesecurity.shift.parser.Parser;

class SymbolsFile {

  private final Path path;
  private final Module module;
  private final List<ImportSpecifier> imports = new ArrayList<>();
  private final List<ExportSpecifier> exports = new ArrayList<>();
  private final List<Statement> code = new ArrayList<>();

  public Path getPath() {
    return path;
  }

  public SymbolsFile(Path path) {
    this.path = path;

    try {
      module = Parser.parseModule(FileUtil.pathToString(path));
    } catch (JsError e) {
      throw new LdException(
        "Error parsing symbols file " + path + ": " + e,
        e
      );
    }

    ModuleUtil.validateModule(module, path.toString());
    ModuleUtil.extractImportsExports(
      module,
      path.toString(),
      code,
      imports,
      exports
    );

    for (ImportSpecifier is : imports) {
      SymbolTable.Symbol symbol = SymbolTable.INSTANCE.addUndefined(
        is.name.orJust(is.binding.name)
      );
      symbol.markUsed();
    }
    for (ExportSpecifier es : exports) {
      SymbolTable.INSTANCE.addDefined(
        es.exportedName,
        new SymbolTable.JsDefinition(this)
      );
    }
  }

  public void appendModule(
    ModuleGenerator generator,
    List<Statement> statements
  ) {
    SymbolTable symbolTable = SymbolTable.INSTANCE;
    List<ExportSpecifier> usedExports = exports.stream()
      .filter(es -> symbolTable.getSymbol(es.exportedName).isUsed())
      .collect(Collectors.toList());
    if (usedExports.isEmpty())
      return;

    List<Statement> moduleStatements = new ArrayList<>();
    generator.appendImports(moduleStatements, imports);
    moduleStatements.addAll(code);
    generator.appendExports(
      moduleStatements,
      usedExports,
      new IdentifierExpression(ModuleGenerator.SYMBOLS_VAR)
    );

    // (function() { <MODULE_BODY>; <EXTRA_EXPORTS>; })()
    statements.add(new ExpressionStatement(
      new CallExpression(
        new FunctionExpression(
          Maybe.empty(),
          false,
          new FormalParameters(ImmutableList.empty(), Maybe.empty()),
          new FunctionBody(
            module.directives,
            ImmutableList.from(moduleStatements)
          )
        ),
        ImmutableList.empty()
      )
    ));
  }

}
