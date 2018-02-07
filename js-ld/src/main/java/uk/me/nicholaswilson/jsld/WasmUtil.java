package uk.me.nicholaswilson.jsld;

import uk.me.nicholaswilson.jsld.wasm.*;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

class WasmUtil {

  public static final byte TYPE_SECTION_ID = 1;
  public static final byte IMPORT_SECTION_ID = 2;
  public static final byte FUNCTION_SECTION_ID = 3;
  public static final byte TABLE_SECTION_ID = 4;
  public static final byte MEMORY_SECTION_ID = 5;
  public static final byte GLOBAL_SECTION_ID = 6;
  public static final byte EXPORT_SECTION_ID = 7;

  private static final CharsetDecoder UTF_8_DECODER =
      StandardCharsets.UTF_8.newDecoder();

  private static final byte OPCODE_END = 0x0B;
  private static final byte OPCODE_GET_GLOBAL = 0x23;
  private static final byte OPCODE_CONST_I32 = 0x41;
  private static final byte OPCODE_CONST_I64 = 0x42;
  private static final byte OPCODE_CONST_F32 = 0x43;
  private static final byte OPCODE_CONST_F64 = 0x44;

  static {
    UTF_8_DECODER.onMalformedInput(CodingErrorAction.REPORT);
    UTF_8_DECODER.onUnmappableCharacter(CodingErrorAction.REPORT);
  }

  public static int getUleb32(ByteBuffer buffer) {
    long bits = getLeb32(buffer, false);
    if ((bits & ~0xffff_ffffL) != 0) {
      // The last byte cannot have the top three bits set, which would take the
      // value out of a U32 range.
      throw new LdException("Invalid Wasm file: bad ULEB32");
    }
    if ((bits & 0x8000_0000L) != 0)
      throw new LdException("Unsupported Wasm file: ULEB32 >= 2^31");
    return (int)bits;
  }

  public static int getSleb32(ByteBuffer buffer) {
    long bits = getLeb32(buffer, true);
    long sign = bits & ~0x7fff_ffffL;
    if (sign != 0 && sign != ~0x7fff_ffffL) {
      // The top three bits must be set to the same value, or else the value will be out of the
      // S32 range.
      throw new LdException("Invalid Wasm file: bad SLEB32");
    }
    return (int)bits;
  }

  private static long getLeb32(ByteBuffer buffer, boolean signExtend) {
    long bits = 0;
    int shift = 0;
    for (int i = 0; i < 5; ++i) {
      byte b = buffer.get();
      bits |= (long)(b & 0x7f) << shift;
      if ((b & 0x80) == 0) {
        if (signExtend && (b & 0x40) != 0)
          bits |= (~0) << (shift + 7);
        return bits;
      }
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

  public static WasmTableSignature getTableType(ByteBuffer buffer) {
    return new WasmTableSignature(WasmElemType.of(buffer.get()), getLimits(buffer));
  }

  public static WasmLimits getLimits(ByteBuffer buffer) {
      return getBoolean(buffer)
        ? new WasmLimits(getUleb32(buffer), getUleb32(buffer))
        : new WasmLimits(getUleb32(buffer));
  }

  public static WasmGlobalSignature getGlobalType(ByteBuffer buffer) {
    return new WasmGlobalSignature(WasmValType.of(buffer.get()), getBoolean(buffer));
  }

  public static void skipGlobalInit(ByteBuffer buffer) {
    byte op;
    while ((op = buffer.get()) != OPCODE_END) {
      if (op == OPCODE_GET_GLOBAL) {
        getUleb32(buffer);
      } else if (op == OPCODE_CONST_I32) {
        buffer.getInt();
      } else if (op == OPCODE_CONST_F32) {
        buffer.getFloat();
      } else if (op == OPCODE_CONST_I64) {
        buffer.getLong();
      } else if (op == OPCODE_CONST_F64) {
        buffer.getDouble();
      } else {
        throw new LdException("Invalid Wasm file: bad constant initialiser");
      }
    }
  }

  private static boolean getBoolean(ByteBuffer buffer) {
    byte flag = buffer.get();
    if (flag == 0x00) {
      return false;
    } else if (flag == 0x01) {
      return true;
    } else {
      throw new LdException("Invalid Wasm file: bad boolean flag");
    }
  }

  public static WasmFunctionSignature getFuncType(ByteBuffer buffer) {
    // XXX
  }


  private WasmUtil() {
  }

}
