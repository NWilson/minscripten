package uk.me.nicholaswilson.jsld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.functional.data.Maybe;
import com.shapesecurity.shift.ast.*;
import com.shapesecurity.shift.codegen.PrettyCodeGen;

public class ModuleGenerator {

  // For good hygiene, I'm using double-underscore for anything that might
  // collide with names used by customer code.  For example, someone could
  // import an external module called "jQuery", or maybe there's some wildly
  // popular third-party script called "symbols"...  Thus I'm trying to keep to
  // an absolute minimum any pollution of the namespace that modules are nested
  // inside, and use underscores for those symbols to make sure.
  private static final String CURRENT_SCRIPT_VAR = "__currentScript";
  private static final String FETCH_VAR = "__fetch";
  private static final String EXPORTS_VAR = "__exports";
  private static final String SYMBOLS_VAR = "__symbols";

  private List<Statement> script = new ArrayList<>();
  private final List<SymbolsFile> symbolsFiles;
  private final WasmFile wasmFile;
  private final String moduleName;

  public ModuleGenerator(
    List<SymbolsFile> symbolsFiles,
    WasmFile wasmFile,
    String moduleName
  ) {
    this.symbolsFiles = symbolsFiles;
    this.wasmFile = wasmFile;
    this.moduleName = moduleName;
  }

  public String generate() {
    generatePreamble();
    generateJsSymbols();
    generatePostamble();
    generateWrapper();
    return generateScript();
  }


  private void generatePreamble() {
    ModuleUtil.appendFragment(
      script,
      "var " + EXPORTS_VAR + " = {};\n" +
        "var " + SYMBOLS_VAR + " = {};"
    );
  }

  private void generateJsSymbols() {
    for (SymbolsFile symbolsFile : symbolsFiles) {
      symbolsFile.appendModule(script, SYMBOLS_VAR);
    }
  }

  private void generatePostamble() {
    // Construct an expression that will calculate the filepath for the WASM
    // module, relative to the current script, and using the filename it has
    // on disk right now.
    //  -> new URL("<WASM_FILENAME>", __currentScript.src).toString()
    String wasmFileName = wasmFile.getPath().getFileName().toString();
    CallExpression wasmUrlExpression = new CallExpression(
      new StaticMemberExpression(
        "toString",
        new NewExpression(
          new IdentifierExpression("URL"),
          ImmutableList.of(
            new LiteralStringExpression(wasmFileName),
            new StaticMemberExpression(
              "src",
              new IdentifierExpression(CURRENT_SCRIPT_VAR)
            )
          )
        )
      ),
      ImmutableList.empty()
    );

    CallExpression ce = new CallExpression(
      new IdentifierExpression(FETCH_VAR),
      ImmutableList.of(wasmUrlExpression)
    );

    ce = new CallExpression(
      new StaticMemberExpression("then", ce),
      ImmutableList.of(ModuleUtil.parseFragmentExpression(
        "function(response) { return response.arrayBuffer(); }"
      ))
    );

    ce = new CallExpression(
      new StaticMemberExpression("then", ce),
      ImmutableList.of(ModuleUtil.parseFragmentExpression(
        "function(bytes) { return WebAssembly.compile(bytes); }"
      ))
    );

    ce = new CallExpression(
      new StaticMemberExpression("then", ce),
      ImmutableList.of(ModuleUtil.parseFragmentExpression(
        // XXX do something with it - create the memory!
        "function(wasmModule) {\n" +
          "  return WebAssembly.instantiate(wasmModule, { \"env\" : " +
          SYMBOLS_VAR + "});\n" + "}"
      ))
    );

    String wasmInstanceVar = "wasmInstance";
    String exportsVar = "es";
    List<Statement> wasmInstanceStatements = new ArrayList<>();
    ModuleUtil.appendFragment(
      wasmInstanceStatements,
      "var " + exportsVar + " = " + wasmInstanceVar + "['exports'];"
    );
    wasmFile.appendExports(wasmInstanceStatements, SYMBOLS_VAR, exportsVar);
    ModuleUtil.appendFragment(
      wasmInstanceStatements,
      "return Object.freeze(" + EXPORTS_VAR + ");"
    );
    ce = new CallExpression(
      new StaticMemberExpression("then", ce),
      ImmutableList.of(
        new FunctionExpression(
          Maybe.empty(),
          false,
          new FormalParameters(
            ImmutableList.of(new BindingIdentifier(wasmInstanceVar)),
            Maybe.empty()
          ),
          new FunctionBody(
            ImmutableList.empty(),
            ImmutableList.from(wasmInstanceStatements)
          )
        )
      )
    );

    script.add(new ReturnStatement(Maybe.of(ce)));
  }

  private void generateWrapper() {
    List<ImportSpecifier> imports = Collections.emptyList(); // XXX
    String rootVar = "root";
    String factoryVar = "factory";

    Statement amdFunctionBody = new IfStatement(
      ModuleUtil.parseFragmentExpression(
        "typeof define === 'function' && define['amd']"
      ),
      new ExpressionStatement(
        // define("<OUTPUT_NAME>", ["IMPORTS_FROM"], factory);
        new CallExpression(
          new IdentifierExpression("define"),
          ImmutableList.of(
            new LiteralStringExpression(moduleName),
            new ArrayExpression(
              ImmutableList.from(
                imports.stream()
                  .map(i -> i.name.orJust(i.binding.name))
                  .map(i ->
                    (SpreadElementExpression)new LiteralStringExpression(i))
                  .map(Maybe::of)
                  .collect(Collectors.toList())
              )
            ),
            new IdentifierExpression(factoryVar)
          )
        )
      ),
      Maybe.of(new ExpressionStatement(
        // root["<OUTPUT_NAME>"] = factory(root["<IMPORTS_FROM>"]);
        new AssignmentExpression(
          new ComputedMemberExpression(
            new LiteralStringExpression(moduleName),
            new IdentifierExpression(rootVar)
          ),
          new CallExpression(
            new IdentifierExpression(factoryVar),
            ImmutableList.from(
              imports.stream()
                .map(i -> i.name.orJust(i.binding.name))
                .map(i -> new ComputedMemberExpression(
                  new LiteralStringExpression(i),
                  new IdentifierExpression(rootVar)
                ))
                .collect(Collectors.toList())
            )
          )
        )
      ))
    );

    // (function(__currentScript, __fetch, IMPORTS_AS) {
    //   <BODY>
    // }).bind(document.currentScript, this.fetch)
    Expression factoryExpression = new CallExpression(
      new StaticMemberExpression(
        "bind",
        new FunctionExpression(
          Maybe.empty(),
          false,
          new FormalParameters(
            ImmutableList.cons(
              new BindingIdentifier(CURRENT_SCRIPT_VAR),
              ImmutableList.cons(
                new BindingIdentifier(FETCH_VAR),
                ImmutableList.from(
                  imports.stream()
                    .map(i -> i.binding.name)
                    .map(BindingIdentifier::new)
                    .collect(Collectors.toList())
                )
              )
            ),
            Maybe.empty()
          ),
          new FunctionBody(
            ImmutableList.empty(),
            ImmutableList.from(script)
          )
        )
      ),
      ImmutableList.of(
        new ComputedMemberExpression(
          new LiteralStringExpression("currentScript"),
          new IdentifierExpression("document")
        ),
        new ComputedMemberExpression(
          new LiteralStringExpression("fetch"),
          new ThisExpression()
        )
      )
    );

    // (function(root, factory) { <AMD_FUNCTION_BODY> })(this, <FACTORY>)
    CallExpression amdRunner = new CallExpression(
      new FunctionExpression(
        Maybe.empty(),
        false,
        new FormalParameters(
          ImmutableList.of(
            new BindingIdentifier(rootVar),
            new BindingIdentifier(factoryVar)
          ),
          Maybe.empty()
        ),
        new FunctionBody(
          ImmutableList.empty(),
          ImmutableList.of(amdFunctionBody)
        )
      ),
      ImmutableList.of(
        new ThisExpression(),
        factoryExpression
      )
    );

    script = Collections.singletonList(new ExpressionStatement(amdRunner));
  }

  private String generateScript() {
    return PrettyCodeGen.codeGen(
      new Script(
        ImmutableList.from(new Directive("use strict")),
        ImmutableList.from(script)
      )
    );
  }

}
