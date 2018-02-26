package uk.me.nicholaswilson.jsld;

import com.shapesecurity.functional.data.Maybe;
import com.shapesecurity.shift.ast.BindingIdentifier;
import com.shapesecurity.shift.ast.ImportSpecifier;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class RequirementsTable {

  private final Map<String, Requirement> requirements = new LinkedHashMap<>();
  private final Pattern ALLOWED_LETTERS = Pattern.compile("[0-9a-zA-Z_$]");

  public static final RequirementsTable INSTANCE = new RequirementsTable();

  public Requirement add(String specifier) {
    return requirements.computeIfAbsent(
      specifier,
      s -> new Requirement(s, budgeSpecifier(s))
    );
  }

  public Requirement get(String specifier) {
    Requirement requirement = requirements.get(specifier);
    assert(requirement != null);
    return requirement;
  }

  public List<ImportSpecifier> getImports() {
    return requirements.values().stream()
      .map(r -> new ImportSpecifier(
        Maybe.of(r.specifier),
        new BindingIdentifier(r.variableName)
      ))
      .collect(Collectors.toList());
  }

  private String budgeSpecifier(String specifier) {
    StringBuilder sb = new StringBuilder();
    sb.append("__");
    specifier.codePoints()
      .filter(c -> c == '$' || c == '_' || Character.isLetterOrDigit(c))
      .forEachOrdered(sb::appendCodePoint);
    return ModuleGenerator.mungeSymbol(sb.toString());
  }


  static class Requirement {

    public final String specifier;
    public final String variableName;

    public Requirement(String specifier, String variableName) {
      this.specifier = specifier;
      this.variableName = variableName;
    }

  }


  private RequirementsTable() {
  }

}
