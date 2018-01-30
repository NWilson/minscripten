package uk.me.nicholaswilson.jsld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.functional.data.Maybe;
import com.shapesecurity.shift.ast.*;
import com.shapesecurity.shift.parser.JsError;
import com.shapesecurity.shift.parser.Parser;
import com.shapesecurity.shift.validator.ValidationError;
import com.shapesecurity.shift.validator.Validator;

public class ModuleUtil {

  /**
   * Validates that the given Module is correct.
   */
  public static void validateModule(Module module, String fileName) {
    Iterable<ValidationError> validationErrors = Validator.validate(module);
    if (validationErrors.iterator().hasNext()) {
      StringBuilder sb = new StringBuilder();
      for (ValidationError error : validationErrors) {
        sb.append("\n  ").append(error.message);
        sb.append(" at ").append(error.node);
      }
      throw new LdException(
        "Error validating module '" + fileName + "':" + sb.toString()
      );
    }
  }

  /**
   * Traverses the given Module, and pulls out its exports into a list of
   * ExportSpecifiers, and also writes out a new list of nodes which make up the
   * Module after the exports are removed.
   * @param module     The input Module
   * @param moduleOut  The output of the Module's contents stripped of exports
   * @param exportsOut The output of the Module's exports
   */
  public static void extractExports(
    Module module,
    String moduleFileName,
    List<Statement> moduleOut,
    List<ExportSpecifier> exportsOut
  ) {
    for (ImportDeclarationExportDeclarationStatement s : module.items) {
      if (s instanceof ImportDeclaration) {
        throw new LdException(
          "Module cannot contain 'import': " + moduleFileName
        );
      }

      if (!(s instanceof ExportDeclaration)) {
        moduleOut.add((Statement)s);
        continue;
      }

      if (s instanceof ExportDefault) {
        throw new LdException(
          "Module cannot contain 'export default': " + moduleFileName
        );
      }
      if (s instanceof ExportAllFrom) {
        throw new LdException(
          "Module cannot contain 'export *': " + moduleFileName
        );
      }

      if (s instanceof ExportFrom) {
        ExportFrom e = (ExportFrom)s;
        if (e.moduleSpecifier.isJust()) {
          throw new LdException(
            "Module cannot contain 'export from': " + moduleFileName
          );
        }
        for (ExportSpecifier es : e.namedExports) {
          exportsOut.add(es);
        }
        continue;
      }

      assert (s instanceof Export);
      Export e = (Export)s;

      List<String> names;
      if (e.declaration instanceof ClassDeclaration) {
        ClassDeclaration cd = (ClassDeclaration) e.declaration;
        moduleOut.add(cd);
        names = Collections.singletonList(cd.name.name);
      } else if (e.declaration instanceof FunctionDeclaration) {
        FunctionDeclaration fd = (FunctionDeclaration)e.declaration;
        moduleOut.add(fd);
        names = Collections.singletonList(fd.name.name);
      } else {
        assert (e.declaration instanceof VariableDeclaration);
        VariableDeclaration vd = (VariableDeclaration)e.declaration;
        moduleOut.add(new VariableDeclarationStatement(vd));
        names = new ArrayList<>();
        for (VariableDeclarator v : vd.declarators) {
          if (v.binding instanceof ObjectBinding) {
            // TODO
            throw new LdException("Unsupported object binding - FIXME");
          } else if (v.binding instanceof ArrayBinding) {
            // TODO
            throw new LdException("Unsupported array binding - FIXME");
          } else {
            assert (v.binding instanceof BindingIdentifier);
            names.add(((BindingIdentifier)v.binding).name);
          }
        }
      }
      for (String name : names) {
        exportsOut.add(new ExportSpecifier(Maybe.empty(), name));
      }
    }
  }

  public static void appendFragment(List<Statement> script, String fragment) {
    try {
      ImmutableList<Statement> statements =
        Parser.parseScript("function f() { " + fragment + " }").statements;
      assert(statements.maybeTail().isNothing());
      statements =
        ((FunctionDeclaration)statements.maybeHead().orJust(null))
          .body.statements;
      for (Statement st : statements) {
        script.add(st);
      }
    } catch (JsError e) {
      throw new LdException(
        "Unable to parse fragment '" + fragment + "': " + e
      );
    }
  }

  public static Expression parseFragmentExpression(String fragment) {
    try {
      ImmutableList<Statement> statements =
        Parser.parseScript("(" + fragment + ")").statements;
      assert(statements.maybeTail().isNothing());
      return ((ExpressionStatement)statements.maybeHead().orJust(null))
        .expression;
    } catch (JsError e) {
      throw new LdException(
        "Unable to parse fragment '" + fragment + "': " + e
      );
    }
  }


  private ModuleUtil() {
  }

}
