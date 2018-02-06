package uk.me.nicholaswilson.jsld;

import uk.me.nicholaswilson.jsld.wasm.*;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public class WasmUtil {

  public static final byte IMPORT_SECTION_ID = 2;
  public static final byte EXPORT_SECTION_ID = 7;
  private static final CharsetDecoder UTF_8_DECODER =
    StandardCharsets.UTF_8.newDecoder();
  static {
    UTF_8_DECODER.onMalformedInput(CodingErrorAction.REPORT);
    UTF_8_DECODER.onUnmappableCharacter(CodingErrorAction.REPORT);
  }

  public static int getUleb32(ByteBuffer buffer) {
    long bits = getLeb32(buffer);
    if ((bits & ~0xffff_ffffl) != 0) {
      // The last byte cannot have the top three bits set, which would take the
      // value of a U32 range
      throw new LdException("Invalid Wasm file: bad ULEB32");
    }
    if ((bits & 0x8000_0000l) != 0)
      throw new LdException("Unsupported Wasm file: ULEB32 >= 2^31");
    return (int)bits;
  }

  public static long getLeb32(ByteBuffer buffer) {
    long bits = 0;
    int shift = 0;
    for (int i = 0; i < 5; ++i) {
      byte b = buffer.get();
      bits |= (long)(b & 0x7f) << shift;
      if ((b & 0x80) == 0)
        return bits;
      shift += 7;
    }
    // A value in the range 0..2^32-1 cannot take more than five bytes
    throw new LdException("Invalid Wasm file: bad LEB32");
  }

  public static String getString(ByteBuffer buffer) {
    int len = getUleb32(buffer);
    int oldLimit = buffer.limit();
    buffer.limit(buffer.position() + len);
    try {
      CharBuffer decoded = UTF_8_DECODER.decode(buffer);
      assert(!buffer.hasRemaining());
      buffer.limit(oldLimit);
      return decoded.toString();
    } catch (CharacterCodingException e) {
      throw new LdException("Invalid Wasm file: bad UTF-8", e);
    }
  }

  public static int getTypeIdx(ByteBuffer buffer) {
    return getUleb32(buffer);
  }

  public static WasmTableType getTableType(ByteBuffer buffer) {
    return new WasmTableType(WasmElemType.of(buffer.get()), getLimits(buffer));
  }

  public static WasmLimits getLimits(ByteBuffer buffer) {
      return getBoolean(buffer)
        ? new WasmLimits(getUleb32(buffer), getUleb32(buffer))
        : new WasmLimits(getUleb32(buffer));
  }

  public static WasmGlobalType getGlobalType(ByteBuffer buffer) {
    return new WasmGlobalType(WasmValType.of(buffer.get()), getBoolean(buffer));
  }

  public static boolean getBoolean(ByteBuffer buffer) {
    byte flag = buffer.get();
    if (flag == 0x00) {
      return false;
    } else if (flag == 0x01) {
      return true;
    } else {
      throw new LdException("Invalid Wasm file: bad boolean flag");
    }
  }


  private WasmUtil() {
  }

}
