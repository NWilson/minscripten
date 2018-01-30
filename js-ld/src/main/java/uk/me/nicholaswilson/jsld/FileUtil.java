package uk.me.nicholaswilson.jsld;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileUtil {

  public static String pathToString(Path path) {
    try {
      byte[] encoded = Files.readAllBytes(path);
      return new String(encoded, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new LdException("Error reading file '" + path + "': " + e, e);
    }
  }


  private FileUtil() {
  }

}
