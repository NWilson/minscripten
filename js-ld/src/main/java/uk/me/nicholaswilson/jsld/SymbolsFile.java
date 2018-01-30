package uk.me.nicholaswilson.jsld;

import java.nio.file.Path;
import java.util.*;

import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.functional.data.Maybe;
import com.shapesecurity.shift.ast.*;
import com.shapesecurity.shift.parser.JsError;
import com.shapesecurity.shift.parser.Parser;
import com.shapesecurity.shift.validator.ValidationError;
import com.shapesecurity.shift.validator.Validator;

public class SymbolsFile {

  final Path fileName;
  final Module module;
  final List<ExportSpecifier> exports = new ArrayList<>();
  final List<Node> code = new ArrayList<>();

  public SymbolsFile(Path fileName) {
    try {
      this.fileName = fileName;
      module = Parser.parseModule(Util.pathToString(fileName));
    } catch (JsError e) {
      throw new LdException("Error parsing symbols file: " + e.getMessage(), e);
    }

    Iterable<ValidationError> validationErrors = Validator.validate(module);
    if (validationErrors.iterator().hasNext()) {
      StringBuilder sb = new StringBuilder();
      for (ValidationError error : validationErrors) {
        sb.append("\n  ").append(error.message);
        sb.append(" at ").append(error.node);
      }
      throw new LdException("Error validating symbols file:" + sb.toString());
    }

    validateModule();
  }

  private void validateModule() {
    for (ImportDeclarationExportDeclarationStatement s : module.items) {
      if (s instanceof ImportDeclaration)
        throw new LdException("Symbols file cannot contain import: " + fileName);

      if (!(s instanceof ExportDeclaration)) {
        code.add(s);
        continue;
      }

      if (s instanceof ExportDefault) {
        throw new LdException("Symbols file cannot contain export default: " +
          fileName);
      }
      if (s instanceof ExportAllFrom) {
        throw new LdException("Symbols file cannot contain export *: " +
          fileName);
      }

      if (s instanceof ExportFrom) {
        ExportFrom e = (ExportFrom)s;
        if (e.moduleSpecifier.isJust())
          throw new LdException("Symbols file cannot contain export from: " +
            fileName);
        for (ExportSpecifier es : e.namedExports)
          exports.add(es);
        continue;
      }

      assert(s instanceof Export);
      Export e = (Export)s;
      code.add(e.declaration);
      List<String> names;
      if (e.declaration instanceof ClassDeclaration) {
        names = Collections.singletonList(
          ((ClassDeclaration)e.declaration).name.name);
      } else if (e.declaration instanceof FunctionDeclaration) {
        names = Collections.singletonList(
          ((FunctionDeclaration)e.declaration).name.name);
      } else {
        assert(e.declaration instanceof VariableDeclaration);
        names = new ArrayList<>();
        for (VariableDeclarator vd :
          ((VariableDeclaration)e.declaration).declarators) {
          if (vd.binding instanceof ObjectBinding) {
            // TODO
            throw new LdException("Unsupported object binding - sorry, lazy");
          } else if (vd.binding instanceof ArrayBinding) {
            // TODO
            throw new LdException("Unsupported array binding - sorry, lazy");
          } else {
            assert(vd.binding instanceof BindingIdentifier);
            names.add(((BindingIdentifier)vd.binding).name);
          }
        }
      }
      for (String name : names)
        exports.add(new ExportSpecifier(Maybe.empty(), name));
    }

    for (ExportSpecifier es : exports) {
      SymbolTable.INSTANCE.addSymbol(
        new SymbolTable.JsDefinition(es.exportedName, this));
    }
  }

}
