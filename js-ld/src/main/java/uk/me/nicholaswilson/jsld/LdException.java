package uk.me.nicholaswilson.jsld;

public class LdException extends RuntimeException {

  public LdException(String message) {
    super(message);
  }

  public LdException(String message, Throwable cause) {
    super(message, cause);
  }

}
