package uk.me.nicholaswilson.jsld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.functional.data.Maybe;
import com.shapesecurity.shift.ast.*;
import com.shapesecurity.shift.scope.GlobalScope;
import com.shapesecurity.shift.scope.ScopeAnalyzer;
import com.shapesecurity.shift.scope.Variable;
import uk.me.nicholaswilson.jsld.SymbolTable.MemoryDefinition;
import uk.me.nicholaswilson.jsld.wasm.WasmLimits;

class ModuleGenerator {

  // For good hygiene, I'm using double-underscore for anything that might
  // collide with names used by customer code.  For example, someone could
  // import an external module called "jQuery", or maybe there's some wildly
  // popular third-party script called "symbols"...  Thus I'm trying to keep to
  // an absolute minimum any pollution of the namespace that modules are nested
  // inside, and use underscores for those symbols to make sure.
  public static final String ROOT_VAR = "__root";
  public static final String CURRENT_SCRIPT_VAR = "__currentScript";
  public static final String EXPORTS_VAR = "__exports";
  public static final String SYMBOLS_VAR = "__symbols";

  private List<Statement> scriptStatements = new ArrayList<>();
  private final List<SymbolsFile> symbolsFiles;
  private final List<ExportsFile> exportsFiles;
  private final WasmFile wasmFile;
  private final List<MemoryDefinition> memoryDefinitions;
  private final String moduleName;
  private final Set<String> externs;

  public ModuleGenerator(
    List<SymbolsFile> symbolsFiles,
    List<ExportsFile> exportsFiles,
    WasmFile wasmFile,
    List<MemoryDefinition> memoryDefinitions,
    String moduleName,
    Set<String> externs
  ) {
    this.symbolsFiles = symbolsFiles;
    this.exportsFiles = exportsFiles;
    this.wasmFile = wasmFile;
    this.memoryDefinitions = memoryDefinitions;
    this.moduleName = moduleName;
    this.externs = externs;
  }

  public Script generate() {
    generatePreamble();
    generateJsSymbols();
    generateJsExports();
    generatePostamble();
    generateWrapper();
    Script script = generateScript();
    analyzeExterns(script);
    return script;
  }

  private void analyzeExterns(Script script) {
    // Run the scope analyzer to detect for any dodgy use of global variables
    // that shouldn't be allowed (eg a global variable leak).
    GlobalScope scope = ScopeAnalyzer.analyze(script);
    List<Variable> bannedVariables = scope.variables().stream()
      .filter(v -> !externs.contains(v.name))
      .collect(Collectors.toList());

    if (!bannedVariables.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      for (Variable variable : bannedVariables) {
        sb.append("\n  ").append(variable.name);
      }
      throw new LdException(
        "Error - module contains unbound variables:" + sb.toString()
      );
    }
  }


  private void generatePreamble() {
    ModuleUtil.appendFragment(
      scriptStatements,
      "const " + EXPORTS_VAR + " = {};\n" +
        "const " + SYMBOLS_VAR + " = {};"
    );
  }

  private void generateJsSymbols() {
    for (SymbolsFile symbolsFile : symbolsFiles) {
      symbolsFile.appendModule(this, scriptStatements);
    }
  }

  private void generateJsExports() {
    for (ExportsFile exportsFile : exportsFiles) {
      exportsFile.appendModule(this, scriptStatements);
    }
  }

  private void generatePostamble() {
    // Construct an expression that will calculate the file path for the WASM
    // module, relative to the current scriptStatements, and using the filename it has
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
      new IdentifierExpression("fetch"),
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
      ImmutableList.of(generateInstantiation())
    );

    String wasmInstanceVar = "wasmInstance";
    String exportsVar = "es";
    List<Statement> wasmInstanceStatements = new ArrayList<>();
    ModuleUtil.appendFragment(
      wasmInstanceStatements,
      "const " + exportsVar + " = " + wasmInstanceVar + ".exports;"
    );
    wasmFile.appendExports(wasmInstanceStatements, exportsVar);
    if (wasmFile.getNeedsExternalCallCtors()) {
      wasmInstanceStatements.add(new ExpressionStatement(
        new CallExpression(
          new ComputedMemberExpression(
            new LiteralStringExpression(WasmFile.CALL_CTORS_SYMBOL),
            new IdentifierExpression(exportsVar)
          ),
          ImmutableList.empty()
        )
      ));
    }
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

    scriptStatements.add(new ReturnStatement(Maybe.of(ce)));
  }

  private Expression generateInstantiation() {
    String moduleVar = "wasmModule";
    List<Statement> instantiationStatements = new ArrayList<>();
    for (MemoryDefinition memoryDefinition : memoryDefinitions) {
      // __symbols['<MEMORY_NAME>'] = new WebAssembly.Memory(limit)
      WasmLimits limits = memoryDefinition.signature.limits;
      ImmutableList<ObjectProperty> limitProperties = ImmutableList.of(
        new DataProperty(
          new LiteralNumericExpression((double)limits.min),
          new StaticPropertyName("initial")
        )
      );
      if (limits.max.isPresent()) {
        limitProperties = ImmutableList.cons(
          new DataProperty(
            new LiteralNumericExpression((double)limits.max.get()),
            new StaticPropertyName("maximum")
          ),
          limitProperties
        );
      }
      instantiationStatements.add(new ExpressionStatement(
        new AssignmentExpression(
          new ComputedMemberExpression(
            new LiteralStringExpression(memoryDefinition.name),
            new IdentifierExpression(ModuleGenerator.SYMBOLS_VAR)
          ),
          new NewExpression(
            new StaticMemberExpression(
              "Memory",
              new IdentifierExpression("WebAssembly")
            ),
            ImmutableList.of(new ObjectExpression(limitProperties))
          )
        )
      ));
    }
    instantiationStatements.add(new ReturnStatement(
      Maybe.of(
        new CallExpression(
          new StaticMemberExpression(
            "instantiate",
            new IdentifierExpression("WebAssembly")
          ),
          ImmutableList.of(
            new IdentifierExpression(moduleVar),
            new ObjectExpression(
              ImmutableList.cons(
                new DataProperty(
                  new IdentifierExpression(SYMBOLS_VAR),
                  new StaticPropertyName(WasmFile.SYMBOLS_MODULE)
                ),
                wasmFile.getImports()
              )
            )
          )
        )
      )
    ));
    return new FunctionExpression(
      Maybe.empty(),
      false,
      new FormalParameters(
        ImmutableList.of(new BindingIdentifier(moduleVar)),
        Maybe.empty()
      ),
      new FunctionBody(
        ImmutableList.empty(),
        ImmutableList.from(instantiationStatements)
      )
    );
  }

  private void generateWrapper() {
    List<ImportSpecifier> imports = RequirementsTable.INSTANCE.getImports();

    String rootVar = "root";
    String factoryVar = "factory";

    FunctionBody amdFunctionBody = new FunctionBody(
      ImmutableList.empty(),
      ImmutableList.of(
        ModuleUtil.parseFragmentStatement("const define = root.define;"),
        new IfStatement(
          ModuleUtil.parseFragmentExpression(
            "typeof define === 'function' && define.amd"
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
        )
      )
    );

    // (function(__root, __currentScript, IMPORTS_AS) {
    //   <BODY>
    // }).bind(null, this, document.currentScript)
    Expression factoryExpression = new CallExpression(
      new StaticMemberExpression(
        "bind",
        new FunctionExpression(
          Maybe.empty(),
          false,
          new FormalParameters(
            ImmutableList.cons(
              new BindingIdentifier(ROOT_VAR),
              ImmutableList.cons(
                new BindingIdentifier(CURRENT_SCRIPT_VAR),
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
            ImmutableList.from(scriptStatements)
          )
        )
      ),
      ImmutableList.cons(
        new LiteralNullExpression(),
        ImmutableList.cons(
          new ThisExpression(),
          ImmutableList.of(
            new StaticMemberExpression(
              "currentScript",
              new IdentifierExpression("document")
            )
          )
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
        amdFunctionBody
      ),
      ImmutableList.of(
        new ThisExpression(),
        factoryExpression
      )
    );

    scriptStatements = Collections.singletonList(new ExpressionStatement(amdRunner));
  }

  private Script generateScript() {
    return new Script(
      ImmutableList.from(new Directive("use strict")),
      ImmutableList.from(scriptStatements)
    );
  }

  public void appendImports(
    List<Statement> statementsOut,
    List<ImportSpecifier> symbolImports,
    List<Import> requirementsImports
  ) {
    List<VariableDeclarator> requirementsDeclarators = new ArrayList<>();
    for (Import i : requirementsImports) {
      RequirementsTable.Requirement r =
        RequirementsTable.INSTANCE.get(i.moduleSpecifier);
      if (i.defaultBinding.isJust() &&
        !i.defaultBinding.fromJust().name.equals(r.variableName)) {
        requirementsDeclarators.add(
          new VariableDeclarator(
            new BindingIdentifier(i.defaultBinding.fromJust().name),
            Maybe.of(new IdentifierExpression(r.variableName))
          )
        );
      }
      for (ImportSpecifier is : i.namedImports) {
        requirementsDeclarators.add(
          new VariableDeclarator(
            new BindingIdentifier(is.binding.name),
            Maybe.of(new ComputedMemberExpression(
              new LiteralStringExpression(is.name.orJust(is.binding.name)),
              new IdentifierExpression(r.variableName)
            ))
          )
        );
      }
    }
    if (!requirementsDeclarators.isEmpty()) {
      statementsOut.add(new VariableDeclarationStatement(
        // const <NAME> = <REQ>
        new VariableDeclaration(
          VariableDeclarationKind.Const,
          ImmutableList.from(requirementsDeclarators)
        )
      ));
    }

    if (!symbolImports.isEmpty()) {
      statementsOut.add(new VariableDeclarationStatement(
        // let <NAME>, ...
        new VariableDeclaration(
          VariableDeclarationKind.Let,
          ImmutableList.from(
            symbolImports.stream()
              .map(is -> new VariableDeclarator(
                new BindingIdentifier(is.binding.name),
                Maybe.empty()
              ))
              .collect(Collectors.toList())
          )
        )
      ));
    }
    for (ImportSpecifier is : symbolImports) {
      // <NAME> = function(...args) { late-bind '<IMPORTED_NAME>' to name }
      statementsOut.add(new ExpressionStatement(
        new AssignmentExpression(
          new BindingIdentifier(is.binding.name),
          ModuleUtil.generateLateBinding(
            new BindingIdentifier(is.binding.name),
            new ComputedMemberExpression(
              new LiteralStringExpression(
                is.name.orJust(is.binding.name)
              ),
              new IdentifierExpression(
                ModuleGenerator.SYMBOLS_VAR
              )
            )
          )
        )
      ));
    }
  }

  public void appendExports(
    List<Statement> statementsOut,
    List<ExportSpecifier> exports,
    IdentifierExpression exportIdentifier
  ) {
    for (ExportSpecifier es : exports) {
      // __exports['<EXPORTED_NAME>'] = <NAME>
      statementsOut.add(new ExpressionStatement(
        new AssignmentExpression(
          new ComputedMemberExpression(
            new LiteralStringExpression(es.exportedName),
            exportIdentifier
          ),
          new IdentifierExpression(es.name.orJust(es.exportedName))
        )
      ));
    }
  }

}
