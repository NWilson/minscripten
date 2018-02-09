package uk.me.nicholaswilson.jsld;

import com.shapesecurity.shift.ast.*;
import com.shapesecurity.shift.parser.JsError;
import com.shapesecurity.shift.parser.Parser;
import com.shapesecurity.shift.scope.ScopeAnalyzer;
import com.shapesecurity.shift.scope.Variable;

import java.nio.file.Path;
import java.util.*;

public class ExternsFile {

  private final Path path;
  private final Set<String> declarations;

  public Path getPath() {
    return path;
  }

  public Set<String> getDeclarations() {
    return declarations;
  }

  public ExternsFile(Path path) {
    this.path = path;

    Script script;
    try {
      script = Parser.parseScript(FileUtil.pathToString(path));
    } catch (JsError e) {
      throw new LdException(
        "Error parsing externs file " + path + ": " + e,
        e
      );
    }

    Set<String> declarations = new HashSet<>();
    for (Variable variable : ScopeAnalyzer.analyze(script).variables()) {
      declarations.add(variable.name);
    }
    this.declarations = Collections.unmodifiableSet(declarations);
  }

}
