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

class ModuleUtil {

  private static final String SYMBOLS_MODULE = "__symbols";


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
   * Traverses the given Module, and pulls out its imports/exports into a
   * list of ImportSpecifiers/ExportSpecifiers, and also writes out a new list
   * of nodes which make up the Module after the imports/exports are removed.
   * @param module     The input Module
   * @param moduleOut  The output of the Module's contents stripped of exports
   * @param importsOut The output of the Module's imports (or null to disallow)
   * @param exportsOut The output of the Module's exports
   */
  public static void extractImportsExports(
    Module module,
    String moduleFileName,
    List<Statement> moduleOut,
    List<ImportSpecifier> importsOut,
    List<ExportSpecifier> exportsOut
  ) {
    for (ImportDeclarationExportDeclarationStatement s : module.items) {
      if (s instanceof ImportDeclaration) {
        if (importsOut == null) {
          throw new LdException(
            "Module cannot contain 'import': " + moduleFileName
          );
        }
        extractImport((ImportDeclaration)s, moduleFileName, importsOut);
        continue;
      }

      if (s instanceof ExportDeclaration) {
        if (exportsOut == null) {
          throw new LdException(
            "Module cannot contain 'export': " + moduleFileName
          );
        }
        extractExport(
          (ExportDeclaration)s,
          moduleFileName,
          moduleOut,
          exportsOut
        );
        continue;
      }

      moduleOut.add((Statement)s);
    }
  }

  private static void extractImport(
    ImportDeclaration id,
    String moduleFileName,
    List<ImportSpecifier> importsOut
  ) {
    if (id instanceof ImportNamespace) {
      // Unsupported, because it doesn't play well with our linking model
      throw new LdException(
        "Module cannot contain 'import *': " + moduleFileName
      );
    }

    assert(id instanceof Import);
    Import i = (Import)id;

    if (i.namedImports.isEmpty()) {
      // Unsupported, because it doesn't play well with our linking model
      throw new LdException(
        "Module cannot contain 'import <all>': " + moduleFileName
      );
    }

    if (i.moduleSpecifier.equals(SYMBOLS_MODULE)) {
      if (i.defaultBinding.isJust()) {
        // Unsupported, because there is no "default" symbol to import!
        throw new LdException(
          "Module cannot contain 'import <default>': " + moduleFileName
        );
      }
    } else {
      // XXX We should allow both exports and symbols modules to import from
      // external libraries, eg jQuery
      throw new LdException(
        "Module can only import symbols from " + SYMBOLS_MODULE + ": " +
          moduleFileName
      );
    }

    for (ImportSpecifier is : i.namedImports)
      importsOut.add(is);
  }

  private static void extractExport(
    ExportDeclaration ed,
    String moduleFileName,
    List<Statement> moduleOut,
    List<ExportSpecifier> exportsOut
  ) {
    if (ed instanceof ExportDefault) {
      // Unsupported, because there is no default symbol!
      throw new LdException(
        "Module cannot contain 'export default': " + moduleFileName
      );
    }

    if (ed instanceof ExportAllFrom) {
      // Unsupported, because it doesn't play well with our linking model
      throw new LdException(
        "Module cannot contain 'export *': " + moduleFileName
      );
    }

    if (ed instanceof ExportFrom) {
      ExportFrom ef = (ExportFrom)ed;
      if (ef.moduleSpecifier.isJust()) {
        // Unsupported because what's the point of exporting symbols like this?
        throw new LdException(
          "Module cannot contain 'export from': " + moduleFileName
        );
      }
      for (ExportSpecifier es : ef.namedExports) {
        exportsOut.add(es);
      }
      return;
    }

    assert(ed instanceof Export);
    Export e = (Export)ed;

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
      assert(e.declaration instanceof VariableDeclaration);
      VariableDeclaration vd = (VariableDeclaration)e.declaration;
      moduleOut.add(new VariableDeclarationStatement(vd));
      names = new ArrayList<>();
      for (VariableDeclarator v : vd.declarators) {
        if (v.binding instanceof ObjectBinding) {
          // TODO just laziness, I have no need for it right now and it's fiddly
          throw new LdException("Unsupported object binding - FIXME");
        } else if (v.binding instanceof ArrayBinding) {
          // TODO just laziness, I have no need for it right now and it's fiddly
          throw new LdException("Unsupported array binding - FIXME");
        } else {
          assert(v.binding instanceof BindingIdentifier);
          names.add(((BindingIdentifier)v.binding).name);
        }
      }
    }
    for (String name : names) {
      exportsOut.add(new ExportSpecifier(Maybe.empty(), name));
    }
  }

  public static void appendFragment(List<Statement> script, String fragment) {
    try {
      ImmutableList<Statement> statements =
        Parser.parseScript("function f() { " + fragment + " }").statements;
      assert(statements.maybeTail().isNothing());
      statements =
        ((FunctionDeclaration)statements.maybeHead().fromJust())
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
      return ((ExpressionStatement)statements.maybeHead().fromJust())
        .expression;
    } catch (JsError e) {
      throw new LdException(
        "Unable to parse fragment '" + fragment + "': " + e
      );
    }
  }

  public static Statement parseFragmentStatement(String fragment) {
    try {
      ImmutableList<Statement> statements =
        Parser.parseScript(fragment).statements;
      assert(statements.maybeTail().isNothing());
      return statements.maybeHead().fromJust();
    } catch (JsError e) {
      throw new LdException(
        "Unable to parse fragment '" + fragment + "': " + e
      );
    }
  }

  public static Expression generateLateBinding(
    BindingIdentifier rebindVar,
    Expression bindingExpression
  ) {
    String argsVar = "args";
    String lateBindingVar = "lateBinding";
    // function(...args) { <BODY> }
    return new FunctionExpression(
      Maybe.empty(),
      false,
      new FormalParameters(
        ImmutableList.empty(),
        Maybe.of(new BindingIdentifier(argsVar))
      ),
      new FunctionBody(
        ImmutableList.empty(),
        ImmutableList.of(
          // const lateBinding = (rebindVar = bindingExpression);
          new VariableDeclarationStatement(
            new VariableDeclaration(
              VariableDeclarationKind.Const,
              ImmutableList.of(
                new VariableDeclarator(
                  new BindingIdentifier(lateBindingVar),
                  Maybe.of(
                    new AssignmentExpression(rebindVar, bindingExpression)
                  )
                )
              )
            )
          ),
          // return lateBinding.call(this, ...args);
          new ReturnStatement(
            Maybe.of(
              new CallExpression(
                new StaticMemberExpression(
                  "call",
                  new IdentifierExpression(lateBindingVar)
                ),
                ImmutableList.of(
                  new ThisExpression(),
                  new SpreadElement(new IdentifierExpression(argsVar))
                )
              )
            )
          )
        )
      )
    );
  }


  private ModuleUtil() {
  }

}
