package uk.me.nicholaswilson.jsld;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.shapesecurity.shift.ast.AssignmentExpression;
import com.shapesecurity.shift.ast.ComputedMemberExpression;
import com.shapesecurity.shift.ast.ExpressionStatement;
import com.shapesecurity.shift.ast.IdentifierExpression;
import com.shapesecurity.shift.ast.LiteralStringExpression;
import com.shapesecurity.shift.ast.Statement;
import uk.me.nicholaswilson.jsld.wasm.WasmObjectType;

import static uk.me.nicholaswilson.jsld.WasmUtil.*;

public class WasmFile {

  private static final String SYMBOLS_MODULE = "env";

  private static class ImportEntry {
    public final String module;
    public final String name;
    public final WasmObjectType type;

    private ImportEntry(String module, String name, WasmObjectType type) {
      this.module = module;
      this.name = name;
      this.type = type;
    }
  }

  private static class ExportEntry {
    public final String name;
    public final WasmObjectType type;

    private ExportEntry(String name, WasmObjectType type) {
      this.name = name;
      this.type = type;
    }
  }

  private final Path path;
  private final List<ImportEntry> imports = new ArrayList<>();
  private final List<ExportEntry> exports = new ArrayList<>();

  public Path getPath() {
    return path;
  }

  public WasmFile(Path path) {
    this.path = path;

    ByteBuffer buffer = FileUtil.pathToByteBuffer(path);
    try {
      buffer.order(ByteOrder.LITTLE_ENDIAN);
      if (buffer.getInt() != 0x6D_73_61_00)
        throw new LdException("Invalid Wasm file: bad magic");
      if (buffer.getInt() != 0x00_00_00_01)
        throw new LdException("Invalid Wasm file: bad version");
      while (buffer.hasRemaining()) {
        byte sectionId = buffer.get();
        int sectionLen = getUleb32(buffer);
        if (sectionId == IMPORT_SECTION_ID) {
          readImports((ByteBuffer)buffer.slice().limit(sectionLen));
        } else if (sectionId == EXPORT_SECTION_ID) {
          readExports((ByteBuffer)buffer.slice().limit(sectionLen));
        }
        buffer.position(buffer.position() + sectionLen);
      }
    } catch (LdException e) {
      throw e;
    } catch (RuntimeException e) {
      // Other ByteBuffer operations, eg. BufferUnderflowException
      throw new LdException("Invalid Wasm file: " + e, e);
    }

    validateNames();

    for (ImportEntry ie : imports) {
      if (!ie.module.equals(SYMBOLS_MODULE))
        continue;
      SymbolTable.Symbol symbol = SymbolTable.INSTANCE.addUndefined(ie.name);
      symbol.markUsed();
    }
    for (ExportEntry ee : exports) {
      SymbolTable.INSTANCE.addDefined(
        ee.name,
        new SymbolTable.WasmDefinition(this)
      );
    }
  }

  public void appendExports(
    List<Statement> statements,
    String exportsName
  ) {
    SymbolTable symbolTable = SymbolTable.INSTANCE;
    for (ExportEntry ee : exports) {
      if (symbolTable.getSymbol(ee.name).isUnused())
        continue;
      // __symbols['<EXPORTED_NAME>'] = exports['<EXPORTED_NAME>']
      statements.add(new ExpressionStatement(
        new AssignmentExpression(
          new ComputedMemberExpression(
            new LiteralStringExpression(ee.name),
            new IdentifierExpression(ModuleGenerator.SYMBOLS_VAR)
          ),
          new ComputedMemberExpression(
            new LiteralStringExpression(ee.name),
            new IdentifierExpression(exportsName)
          )
        )
      ));
    }
  }


  private void readImports(ByteBuffer buffer) {
    int numImports = getUleb32(buffer);
    while ((numImports--) > 0) {
      String module = getString(buffer);
      String name = getString(buffer);
      WasmObjectType type = WasmObjectType.of(buffer.get());
      switch (type) {
        case FUNCTION:
          getTypeIdx(buffer); // ignore type
          break;
        case TABLE:
          getTableType(buffer); // ignore type
          break;
        case MEMORY:
          getLimits(buffer);
          // XXX current prototype of js-ld handles memory export but not import;
          // the actual final thing should handle both, or in fact we should
          // handle import but not export really, since you have to use an
          // imported memory if you want syscalls during startup code to be
          // vaguely useful,
          throw new LdException("XXX handle memory import");
        case GLOBAL:
          getGlobalType(buffer); // ignore type
          break;
      }
      imports.add(new ImportEntry(module, name, type));
    }
  }

  private void readExports(ByteBuffer buffer) {
    int numExports = getUleb32(buffer);
    while ((numExports--) > 0) {
      String name = getString(buffer);
      WasmObjectType type = WasmObjectType.of(buffer.get());
      getUleb32(buffer); // skip the index
      exports.add(new ExportEntry(name, type));
    }
  }

  private void validateNames() {
    Set<String> duplicateSet = new HashSet<>();
    Stream<String> symbolNames = Stream.concat(
      imports.stream()
        .filter(ie -> ie.module.equals(SYMBOLS_MODULE))
        .map(ie -> ie.name),
      exports.stream()
        .map(ee -> ee.name)
    );
    List<String> duplicates = symbolNames
      .filter(s -> !duplicateSet.add(s))
      .collect(Collectors.toList());
    if (!duplicates.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      for (String s : duplicates) {
        sb.append("\n  ").append(s);
      }
      // Could be that a symbol is named twice in the imports, or exported
      // twice, or imported+exported.
      throw new LdException(
        "Wasm file '" + path + "' contains duplicate symbols:" + sb
      );
    }
  }

}
