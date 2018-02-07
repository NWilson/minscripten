package uk.me.nicholaswilson.jsld;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

// The suppressions are because picocli changes values via reflection
@SuppressWarnings({"CanBeFinal", "MismatchedQueryAndUpdateOfCollection"})
@Command(versionProvider = Linker.ManifestVersion.class, name = "js-ld")
public class Linker {

  enum Action {
    GENERATE_IMPORTS,
    LINK
  }

  @Option(
    names = { "--version" },
    versionHelp = true,
    description = "Display version info"
  )
  boolean versionInfoRequested;

  @Option(
    names = { "-h", "--help" },
    usageHelp = true,
    description = "Display this help message"
  )
  boolean usageHelpRequested;

  @Option(
    names = { "-a", "--action" },
    description = "Whether to link (LINK) or perform pre-linking analysis (GENERATE_IMPORTS)",
    paramLabel = "ACTION"
  )
  private Action action = Action.LINK;

  @Option(
    names = { "-o", "--output" },
    required = true,
    description = "The output filename",
    paramLabel = "OUTPUT_FILE"
  )
  private Path outputFilePath;

  @Option(
    names = { "-s", "--symbols" },
    description = "A JavaScript file containing symbols to link to the Wasm module",
    paramLabel = "SYMBOL_FILE"
  )
  private List<Path> symbolsFilePaths = new ArrayList<>();

  @Option(
    names = { "-e", "--exports" },
    description = "A JavaScript file containing methods to export in the JavaScript module",
    paramLabel = "EXPORT_FILE"
  )
  private List<Path> exportsFilePaths = new ArrayList<>();

  @Option(
    names = { "-i", "--import" },
    description = "The allowed imports (free variables) for the module",
    paramLabel = "[BINDING=]NAME"
  )
  private List<String> imports = new ArrayList<>();

  @Parameters(
    index = "0",
    description = "The Wasm module to link against",
    paramLabel = "WASM_MODULE"
  )
  private List<Path> wasmFilePath = new ArrayList<>();


  /** The entry point for the linker */
  public static void main(String[] args) {
    Linker l = new Linker();
    CommandLine cmd = new CommandLine(l);
    cmd.setOverwrittenOptionsAllowed(true);
    try {
      List<Object> results = cmd.parseWithHandlers(
        l.new ArgumentHandler(),
        System.err,
        CommandLine.Help.Ansi.AUTO,
        new CommandLine.DefaultExceptionHandler(),
        args
      );
      if (results.size() == 0)
        System.exit(1);
    } catch (LdException e) {
      System.err.println(e.toString());
      System.exit(1);
    }
  }


  /** Performs import file generation using the configured options */
  private void generateImports() {
    loadSymbols();

    try (
      BufferedWriter writer =
        Files.newBufferedWriter(outputFilePath, StandardCharsets.UTF_8)
    ) {
      for (String symbolName : SymbolTable.INSTANCE.symbolSet())
        writer.append(symbolName).append('\n');
    } catch (IOException e) {
      throw new LdException(
        "Unable to write to " + outputFilePath + ": " + e,
        e
      );
    }
  }

  /** Performs linking using the configured options */
  private void link() {
    List<SymbolsFile> symbolsFiles = loadSymbols();
    List<ExportsFile> exportsFiles = loadExports();
    assert(wasmFilePath.size() == 1);
    WasmFile wasmFile = new WasmFile(wasmFilePath.get(0));
    String moduleName = outputFilePath.getFileName().toString()
      .replaceFirst("\\.[a-z]+$", "");

    SymbolTable.INSTANCE.reportUndefined();

    String moduleStr = new ModuleGenerator(
      symbolsFiles,
      exportsFiles,
      wasmFile,
      moduleName
    ).generate();

    // TODO Run shift-reduce over the whole thing to detect imports, and check
    //   they're declared, and mark imports used

    try (
      BufferedWriter writer =
        Files.newBufferedWriter(outputFilePath, StandardCharsets.UTF_8)
    ) {
      writer.write(moduleStr);
    } catch (IOException e) {
      throw new LdException(
        "Unable to write to " + outputFilePath + ": " + e,
        e
      );
    }
  }


  private List<SymbolsFile> loadSymbols() {
    return symbolsFilePaths.stream()
      .map(SymbolsFile::new)
      .collect(Collectors.toList());
  }

  private List<ExportsFile> loadExports() {
    return exportsFilePaths.stream()
      .map(ExportsFile::new)
      .collect(Collectors.toList());
  }


  private class ArgumentHandler implements CommandLine.IParseResultHandler {

    public List<Object> handleParseResult(
      List<CommandLine> parsedCommands,
      PrintStream out,
      CommandLine.Help.Ansi ansi
    ) throws CommandLine.ExecutionException {
      if (CommandLine.printHelpIfRequested(parsedCommands, out, ansi))
        return Collections.singletonList(null);

      assert (parsedCommands.size() == 1);
      CommandLine cmd = parsedCommands.get(0);
      Linker l = Linker.this;

      switch (l.action) {
      case GENERATE_IMPORTS:
        if (!l.wasmFilePath.isEmpty()) {
          throw new CommandLine.ParameterException(
            cmd,
            "WASM_MODULE specified for pre-link"
          );
        }
        if (!l.exportsFilePaths.isEmpty()) {
          throw new CommandLine.ParameterException(
            cmd,
            "Exports specified for pre-link"
          );
        }
        if (!l.imports.isEmpty()) {
          throw new CommandLine.ParameterException(
            cmd,
            "Imports specified for pre-link"
          );
        }
        l.generateImports();
        break;

      case LINK:
        if (l.wasmFilePath.isEmpty()) {
          throw new CommandLine.ParameterException(
            cmd,
            "WASM_MODULE not specified for link"
          );
        }
        l.link();
        break;
      }

      return Collections.singletonList(null);
    }

  }


  static class ManifestVersion implements CommandLine.IVersionProvider {
    public String[] getVersion() throws Exception {
      Enumeration<URL> resources =
          Linker.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
      while (resources.hasMoreElements()) {
        URL url = resources.nextElement();
        try {
          Manifest manifest = new Manifest(url.openStream());
          if (isApplicableManifest(manifest)) {
            Attributes attr = manifest.getMainAttributes();
            return new String[] {
              get(attr, "Implementation-Title") + " version \"" +
                get(attr, "Implementation-Version") + "\""
            };
          }
        } catch (IOException ex) {
          return new String[] { "Unable to read from " + url + ": " + ex };
        }
      }
      return new String[0];
    }

    private boolean isApplicableManifest(Manifest manifest) {
      Attributes attributes = manifest.getMainAttributes();
      return "js-ld".equals(get(attributes, "Implementation-Title"));
    }

    private static Object get(Attributes attributes, String key) {
      return attributes.get(new Attributes.Name(key));
    }
  }

}
