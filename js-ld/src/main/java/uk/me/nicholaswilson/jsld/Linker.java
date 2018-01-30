package uk.me.nicholaswilson.jsld;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

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
  private Path outputFile;

  @Option(
    names = { "-s", "--symbols" },
    description = "A JavaScript file containing symbols to link to the Wasm module",
    paramLabel = "SYMBOL_FILE"
  )
  private List<Path> symbolFiles = new ArrayList<>();

  @Option(
    names = { "-e", "--exports" },
    description = "A JavaScript file containing methods to export in the JavaScript module",
    paramLabel = "EXPORT_FILE"
  )
  private List<Path> exportFiles = new ArrayList<>();

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
  private List<Path> wasmFile = new ArrayList<>();


  /** The entrypoint for the linker */
  public static void main(String[] args) {
    CommandLine cmd = new CommandLine(new Linker());
    cmd.setOverwrittenOptionsAllowed(true);
    cmd.parseWithHandlers(
      (List<CommandLine> parsedCommands, PrintStream out, CommandLine.Help.Ansi ansi) -> {
        if (CommandLine.printHelpIfRequested(parsedCommands, out, ansi))
          return Collections.emptyList();
        Linker l = parsedCommands.get(0).getCommand();
        switch (l.action) {
        case LINK:
          if (l.wasmFile.isEmpty())
            throw new CommandLine.ParameterException(
              cmd,
              "WASM_MODULE not specified for link"
            );
          l.link();
          break;
        case GENERATE_IMPORTS:
          if (!l.wasmFile.isEmpty())
            throw new CommandLine.ParameterException(
              cmd,
              "WASM_MODULE specified for pre-link"
            );
          if (!l.exportFiles.isEmpty())
            throw new CommandLine.ParameterException(
              cmd,
              "Exports specified for pre-link"
            );
          if (!l.imports.isEmpty())
            throw new CommandLine.ParameterException(
              cmd,
              "Imports specified for pre-link"
            );
          l.generateImports();
          break;
        }
        return Collections.emptyList();
      },
      System.err,
      CommandLine.Help.Ansi.AUTO,
      new CommandLine.DefaultExceptionHandler(),
      args
    );
  }


  /** Performs linking using the configured options */
  public void link() {
    // 1. Load all symbol files
    // 2. Load the Wasm module, and grab its imports/exports
    //    -> Check that we have a symbol for every import
    //    -> Check that symbols don't conflict (across JS, Wasm imports, and Wasm exports)

    // 3. Load all export files

    // 4. Build output
    //    -> Create a module, for each symbols files which have any used symbols
    //    -> This module shall only export the symbols used
    //    -> Instantiate the Wasm loader, pulling out imports from the symbols
    //       and putting exports in there
    //    -> Run shift-reduce over the whole thing to detect imports, and check
    //       they're declared, and mark imports used
    //    -> Write out the output!

  }

  /** Performs import file generation using the configured options */
  public void generateImports() {
    loadSymbols();

    try (BufferedWriter writer = Files.newBufferedWriter(outputFile, Charsets.UTF_8)) {
      for (String symbolName : SymbolTable.INSTANCE.symbolSet())
        writer.append(symbolName).append('\n');
    } catch (IOException e) {
      throw new LdException("Unable to write to " + outputFile + ": " +
        e.getMessage(), e);
    }
  }


  private List<SymbolsFile> loadSymbols() {
    List<SymbolsFile> files = new ArrayList<>();
    for (Path fileName : symbolFiles)
      files.add(new SymbolsFile(fileName));
    return files;
  }


  static class ManifestVersion implements CommandLine.IVersionProvider {
    public String[] getVersion() throws Exception {
      Enumeration<URL> resources = Linker.class.getClassLoader().getResources(
        "META-INF/MANIFEST.MF");
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
