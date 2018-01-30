package uk.me.nicholaswilson.jsld;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.functional.data.Maybe;
import com.shapesecurity.shift.ast.AssignmentExpression;
import com.shapesecurity.shift.ast.BindingIdentifier;
import com.shapesecurity.shift.ast.CallExpression;
import com.shapesecurity.shift.ast.ComputedMemberExpression;
import com.shapesecurity.shift.ast.ExportSpecifier;
import com.shapesecurity.shift.ast.ExpressionStatement;
import com.shapesecurity.shift.ast.FormalParameters;
import com.shapesecurity.shift.ast.FunctionBody;
import com.shapesecurity.shift.ast.FunctionExpression;
import com.shapesecurity.shift.ast.IdentifierExpression;
import com.shapesecurity.shift.ast.LiteralStringExpression;
import com.shapesecurity.shift.ast.Module;
import com.shapesecurity.shift.ast.Statement;
import com.shapesecurity.shift.parser.JsError;
import com.shapesecurity.shift.parser.Parser;

public class SymbolsFile {

  private final Path path;
  private final Module module;
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
    ModuleUtil.extractExports(module, path.toString(), code, exports);

    for (ExportSpecifier es : exports) {
      SymbolTable.INSTANCE.addDefined(
        es.exportedName,
        new SymbolTable.JsDefinition(this)
      );
    }
  }

  public void appendModule(List<Statement> statements, String symbolsName) {
    SymbolTable symbolTable = SymbolTable.INSTANCE;
    List<ExportSpecifier> usedExports = exports.stream()
      .filter(es -> symbolTable.getSymbol(es.exportedName).isUsed())
      .collect(Collectors.toList());
    if (usedExports.isEmpty())
      return;

    List<Statement> moduleStatements = new ArrayList<>(code);

    for (ExportSpecifier es : usedExports) {
      // __symbols['<EXPORTED_NAME>'] = <NAME>
      moduleStatements.add(new ExpressionStatement(
        new AssignmentExpression(
          new ComputedMemberExpression(
            new LiteralStringExpression(es.exportedName),
            new IdentifierExpression(symbolsName)
          ),
          new IdentifierExpression(es.name.orJust(es.exportedName))
        )
      ));
    }

    // (function(__symbols) { <MODULE_BODY>; <EXTRA_EXPORTS>; })(__symbols)
    statements.add(new ExpressionStatement(
      new CallExpression(
        new FunctionExpression(
          Maybe.empty(),
          false,
          new FormalParameters(
            ImmutableList.of(new BindingIdentifier(symbolsName)),
            Maybe.empty()
          ),
          new FunctionBody(
            module.directives,
            ImmutableList.from(moduleStatements)
          )
        ),
        ImmutableList.of(new IdentifierExpression(symbolsName))
      )
    ));
  }

}
