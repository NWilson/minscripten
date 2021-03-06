package uk.me.nicholaswilson.jsld;

import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.functional.data.Maybe;
import com.shapesecurity.shift.ast.*;
import com.shapesecurity.shift.parser.JsError;
import com.shapesecurity.shift.parser.Parser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class ExportsFile {

  private final Path path;
  private final Module module;
  private final List<ImportSpecifier> symbolImports = new ArrayList<>();
  private final List<Import> requirementsImports = new ArrayList<>();
  private final List<ExportSpecifier> exports = new ArrayList<>();
  private final List<Statement> code = new ArrayList<>();

  public Path getPath() {
    return path;
  }

  public ExportsFile(Path path) {
    this.path = path;

    try {
      module = Parser.parseModule(FileUtil.pathToString(path));
    } catch (JsError e) {
      throw new LdException(
        "Error parsing exports file " + path + ": " + e,
        e
      );
    }

    ModuleUtil.validateModule(module, path.toString());
    ModuleUtil.extractImportsExports(
      module,
      path.toString(),
      code,
      symbolImports,
      requirementsImports,
      exports
    );

    for (ImportSpecifier is : symbolImports) {
      SymbolTable.Symbol symbol = SymbolTable.INSTANCE.addUndefined(
        is.name.orJust(is.binding.name)
      );
      symbol.markUsed();
    }
    for (Import i : requirementsImports) {
      RequirementsTable.INSTANCE.add(i.moduleSpecifier);
    }
  }

  public void appendModule(
    ModuleGenerator generator,
    List<Statement> statements
  ) {
    List<Statement> moduleStatements = new ArrayList<>();

    generator.appendImports(
      moduleStatements,
      symbolImports,
      requirementsImports
    );
    moduleStatements.addAll(code);
    generator.appendExports(
      moduleStatements,
      exports,
      new IdentifierExpression(ModuleGenerator.EXPORTS_VAR)
    );

    // (function() {
    //   <MODULE_BODY>; <EXTRA_EXPORTS>;
    // })()
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
