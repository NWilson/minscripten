package uk.me.nicholaswilson.jsld;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.common.base.Charsets;

public class Util {

  public static String pathToString(Path path) {
    try {
      byte[] encoded = Files.readAllBytes(path);
      return new String(encoded, Charsets.UTF_8);
    } catch (IOException e) {
      throw new LdException("Error reading file " + path + ": " + e.getMessage(), e);
    }
  }

}
