package uk.me.nicholaswilson.jsld;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.shapesecurity.functional.data.ImmutableList;
import com.shapesecurity.shift.ast.*;
import uk.me.nicholaswilson.jsld.wasm.*;

import static uk.me.nicholaswilson.jsld.WasmUtil.*;

class WasmFile {

  public static final String SYMBOLS_MODULE = "env";
  public static final String CALL_CTORS_SYMBOL = "__wasm_call_ctors";

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
  private final List<WasmFunctionSignature> functionSignatures = new ArrayList<>(); // FUNCTION section
  private final List<WasmTableSignature> tableSignatures = new ArrayList<>(); // TABLE section
  private final List<WasmMemorySignature> memorySignatures = new ArrayList<>(); // MEMORY section
  private final List<WasmGlobalSignature> globalSignatures = new ArrayList<>(); // GLOBAL section
  private final List<WasmFunctionSignature> functionImports = new ArrayList<>(); // IMPORT section
  private final List<WasmTableSignature> tableImports = new ArrayList<>(); // IMPORT section
  private final List<WasmMemorySignature> memoryImports = new ArrayList<>(); // IMPORT section
  private final List<WasmGlobalSignature> globalImports = new ArrayList<>(); // IMPORT section
  private Integer startFunction; // START section
  private boolean needsExternalCallCtors;

  public Path getPath() {
    return path;
  }

  public boolean getNeedsExternalCallCtors() {
    return needsExternalCallCtors;
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
      int lastSectionId = -1;
      while (buffer.hasRemaining()) {
        byte sectionId = buffer.get();
        int sectionLen = getUleb32(buffer);
        if (sectionId != CUSTOM_SECTION_ID) {
          if (sectionId < lastSectionId)
            throw new LdException("Invalid Wasm file: out of order section");
          lastSectionId = sectionId;
        }
        ByteBuffer sectionBuffer = (ByteBuffer)buffer.slice().limit(sectionLen);
        if (sectionId == TYPE_SECTION_ID) {
          readTypes(sectionBuffer);
        } else if (sectionId == IMPORT_SECTION_ID) {
          readImports(sectionBuffer);
        } else if (sectionId == FUNCTION_SECTION_ID) {
          readFunctions(sectionBuffer);
        } else if (sectionId == TABLE_SECTION_ID) {
          readTables(sectionBuffer);
        } else if (sectionId == MEMORY_SECTION_ID) {
          readMemories(sectionBuffer);
        } else if (sectionId == GLOBAL_SECTION_ID) {
          readGlobals(sectionBuffer);
        } else if (sectionId == EXPORT_SECTION_ID) {
          readExports(sectionBuffer);
        } else if (sectionId == START_SECTION_ID) {
          readStart(sectionBuffer);
        } else {
          sectionBuffer.position(sectionBuffer.limit());
        }
        if (sectionBuffer.hasRemaining())
          throw new LdException("Invalid Wasm file: trailing section data");
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
      if (ie.module.equals(SYMBOLS_MODULE)) {
        SymbolTable.Symbol symbol = SymbolTable.INSTANCE.addUndefined(ie.name);
        symbol.markUsed();
        symbol.setDescriptor(ie.objectDescriptor);
      } else {
        RequirementsTable.INSTANCE.add(ie.module);
      }
    }
    for (ExportEntry ee : exports) {
      SymbolTable.Symbol symbol = SymbolTable.INSTANCE.addDefined(
          ee.name,
          new SymbolTable.WasmDefinition(this)
      );
      symbol.setDescriptor(ee.objectDescriptor);
      if (!hasStartFunction() &&
          symbol.getSymbolName().equals(CALL_CTORS_SYMBOL)) {
        needsExternalCallCtors = true;
      }
    }
  }

  public void appendExports(
    List<Statement> statements,
    String exportsVar,
    String wrapperFunctionVar
  ) {
    SymbolTable symbolTable = SymbolTable.INSTANCE;
    for (ExportEntry ee : exports) {
      if (symbolTable.getSymbol(ee.name).isUnused())
        continue;
      // __symbols['<EXPORTED_NAME>'] = wrapExport('<EXPORTED_NAME>')
      statements.add(new ExpressionStatement(
        new AssignmentExpression(
          new ComputedMemberExpression(
            new LiteralStringExpression(ee.name),
            new IdentifierExpression(ModuleGenerator.SYMBOLS_VAR)
          ),
          ee.objectDescriptor.type == WasmObjectType.FUNCTION
            ? new CallExpression(
              new IdentifierExpression(wrapperFunctionVar),
              ImmutableList.of(new LiteralStringExpression(ee.name))
            )
            : new ComputedMemberExpression(
              new LiteralStringExpression(ee.name),
              new IdentifierExpression(exportsVar)
            )
        )
      ));
    }
  }

  public ImmutableList<ObjectProperty> getImports() {
    Set<String> requirements = new LinkedHashSet<>();
    imports.stream().filter(i -> !i.module.equals(SYMBOLS_MODULE))
      .forEach(i -> requirements.add(i.module));
    return ImmutableList.from(
      requirements.stream().map(i -> {
        RequirementsTable.Requirement r = RequirementsTable.INSTANCE.get(i);
        return (ObjectProperty) new DataProperty(
          new IdentifierExpression(r.variableName),
          new StaticPropertyName(r.specifier)
        );
      }).collect(Collectors.toList())
    );
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
          tableImports.add(signature);
          break;
        }
        case MEMORY: {
          WasmMemorySignature signature = getMemoryType(buffer);
          imports.add(new ImportEntry(module, name, new WasmObjectDescriptor.Memory(signature)));
          memoryImports.add(signature);
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
    int numTables = getUleb32(buffer);
    while ((numTables--) > 0) {
      WasmTableSignature signature = getTableType(buffer);
      tableSignatures.add(signature);
    }
  }

  private void readMemories(ByteBuffer buffer) {
    int numMemories = getUleb32(buffer);
    while ((numMemories--) > 0) {
      WasmMemorySignature signature = getMemoryType(buffer);
      memorySignatures.add(signature);
    }
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
            new ExportEntry(name, new WasmObjectDescriptor.Memory(getMemorySignature(index)))
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

  private void readStart(ByteBuffer buffer) {
    int index = getUleb32(buffer);
    if (index >= functionImports.size() + functionSignatures.size())
      throw new LdException("Invalid Wasm file: bad start section");
    startFunction = index;
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
    int numTableImports = tableImports.size();
    if (index < numTableImports)
      return tableImports.get(index);
    if (index < numTableImports + tableSignatures.size())
      return tableSignatures.get(index - numTableImports);
    throw new LdException("Invalid Wasm file: bad table index");
  }

  private WasmMemorySignature getMemorySignature(int index) {
    int numMemoryImports = memoryImports.size();
    if (index < numMemoryImports)
      return memoryImports.get(index);
    if (index < numMemoryImports + memorySignatures.size())
      return memorySignatures.get(index - numMemoryImports);
    throw new LdException("Invalid Wasm file: bad memory index");
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

  private boolean hasStartFunction() {
    return startFunction != null;
  }

}
