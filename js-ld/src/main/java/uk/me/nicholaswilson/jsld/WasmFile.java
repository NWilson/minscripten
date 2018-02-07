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
import uk.me.nicholaswilson.jsld.wasm.*;

import static uk.me.nicholaswilson.jsld.WasmUtil.*;

class WasmFile {

  private static final String SYMBOLS_MODULE = "env";

  private static class ImportEntry {
    public final String module;
    public final String name;
    public final WasmObjectDescriptor objectDescriptor;

    private ImportEntry(String module, String name, WasmObjectDescriptor objectDescriptor) {
      this.module = module;
      this.name = name;
      this.objectDescriptor = objectDescriptor;
    }
  }

  private static class ExportEntry {
    public final String name;
    public final WasmObjectDescriptor objectDescriptor;

    private ExportEntry(String name, WasmObjectDescriptor objectDescriptor) {
      this.name = name;
      this.objectDescriptor = objectDescriptor;
    }
  }

  private final Path path;
  private final List<ImportEntry> imports = new ArrayList<>();
  private final List<ExportEntry> exports = new ArrayList<>();
  private final List<WasmFunctionSignature> signatures = new ArrayList<>(); // TYPE section
  private final List<WasmFunctionSignature> functionSignatures = new ArrayList<>(); // FUNCTION sect
  private final List<WasmGlobalSignature> globalSignatures = new ArrayList<>(); // GLOBAL section
  private final List<WasmFunctionSignature> functionImports = new ArrayList<>(); // IMPORT section
  private final List<WasmGlobalSignature> globalImports = new ArrayList<>(); // IMPORT section

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
        if (sectionId == TYPE_SECTION_ID) {
          readTypes((ByteBuffer)buffer.slice().limit(sectionLen));
        } else if (sectionId == IMPORT_SECTION_ID) {
          readImports((ByteBuffer)buffer.slice().limit(sectionLen));
        } else if (sectionId == FUNCTION_SECTION_ID) {
          readFunctions((ByteBuffer)buffer.slice().limit(sectionLen));
        } else if (sectionId == TABLE_SECTION_ID) {
          readTables((ByteBuffer)buffer.slice().limit(sectionLen));
        } else if (sectionId == MEMORY_SECTION_ID) {
          readMemories((ByteBuffer)buffer.slice().limit(sectionLen));
        } else if (sectionId == GLOBAL_SECTION_ID) {
          readGlobals((ByteBuffer)buffer.slice().limit(sectionLen));
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
      symbol.setDescriptor(ie.objectDescriptor);
    }
    for (ExportEntry ee : exports) {
      SymbolTable.Symbol symbol = SymbolTable.INSTANCE.addDefined(
          ee.name,
          new SymbolTable.WasmDefinition(this)
      );
      symbol.setDescriptor(ee.objectDescriptor);
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


  private void readTypes(ByteBuffer buffer) {
    int numTypes = getUleb32(buffer);
    while ((numTypes--) > 0) {
      signatures.add(getFuncType(buffer));
    }
  }

  private void readImports(ByteBuffer buffer) {
    int numImports = getUleb32(buffer);
    while ((numImports--) > 0) {
      String module = getString(buffer);
      String name = getString(buffer);
      WasmObjectType type = WasmObjectType.of(buffer.get());
      switch (type) {
        case FUNCTION: {
          int typeIndex = getTypeIdx(buffer);
          if (typeIndex >= signatures.size())
            throw new LdException("Invalid Wasm file: bad fn import type");
          WasmFunctionSignature signature = signatures.get(typeIndex);
          imports.add(new ImportEntry(module, name, new WasmObjectDescriptor.Function(signature)));
          functionImports.add(signature);
          break;
        }
        case TABLE: {
          WasmTableSignature signature = getTableType(buffer);
          imports.add(new ImportEntry(module, name, new WasmObjectDescriptor.Table(signature)));
          break;
        }
        case MEMORY: {
          WasmLimits limits = getLimits(buffer);
          imports.add(new ImportEntry(module, name, new WasmObjectDescriptor.Memory(limits)));
          break;
        }
        case GLOBAL: {
          WasmGlobalSignature signature = getGlobalType(buffer);
          imports.add(new ImportEntry(module, name, new WasmObjectDescriptor.Global(signature)));
          globalImports.add(signature);
          break;
        }
      }
    }
  }

  private void readFunctions(ByteBuffer buffer) {
    int numFunctions = getUleb32(buffer);
    while ((numFunctions--) > 0) {
      int typeIndex = getUleb32(buffer);
      if (typeIndex >= signatures.size())
        throw new LdException("Invalid Wasm file: bad type index");
      functionSignatures.add(signatures.get(typeIndex));
    }
  }

  private void readTables(ByteBuffer buffer) {
    // TODO
  }

  private void readMemories(ByteBuffer buffer) {
    // TODO
  }

  private void readGlobals(ByteBuffer buffer) {
    int numGlobals = getUleb32(buffer);
    while ((numGlobals--) > 0) {
      WasmGlobalSignature signature = getGlobalType(buffer);
      skipGlobalInit(buffer);
      globalSignatures.add(signature);
    }
  }

  private void readExports(ByteBuffer buffer) {
    int numExports = getUleb32(buffer);
    while ((numExports--) > 0) {
      String name = getString(buffer);
      WasmObjectType type = WasmObjectType.of(buffer.get());
      int index = getUleb32(buffer);
      switch (type) {
        case FUNCTION:
          exports.add(
            new ExportEntry(name, new WasmObjectDescriptor.Function(getFunctionSignature(index)))
          );
          break;
        case TABLE:
          exports.add(
            new ExportEntry(name, new WasmObjectDescriptor.Table(getTableSignature(index)))
          );
          break;
        case MEMORY:
          exports.add(
            new ExportEntry(name, new WasmObjectDescriptor.Memory(getMemoryLimits(index)))
          );
          break;
        case GLOBAL:
          exports.add(
            new ExportEntry(name, new WasmObjectDescriptor.Global(getGlobalSignature(index)))
          );
          break;
      }
    }
  }

  private WasmFunctionSignature getFunctionSignature(int index) {
    int numFunctionImports = functionImports.size();
    if (index < numFunctionImports)
      return functionImports.get(index);
    if (index < numFunctionImports + functionSignatures.size())
      return functionSignatures.get(index - numFunctionImports);
    throw new LdException("Invalid Wasm file: bad fn index");
  }

  private WasmTableSignature getTableSignature(int index) {
    return null; // XXX
  }

  private WasmLimits getMemoryLimits(int index) {
    return null; // XXX
  }

  private WasmGlobalSignature getGlobalSignature(int index) {
    int numGlobalImports = globalImports.size();
    if (index < numGlobalImports)
      return globalImports.get(index);
    if (index < numGlobalImports + globalSignatures.size())
      return globalSignatures.get(index - numGlobalImports);
    throw new LdException("Invalid Wasm file: bad global index");
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
