package uk.me.nicholaswilson.jsld.wasm;

import java.util.Optional;

public class WasmLimits {
  public final int min;
  public final Optional<Integer> max;

  public WasmLimits(int min) {
    this.min = min;
    this.max = Optional.empty();
  }

  public WasmLimits(int min, int max) {
    this.min = min;
    this.max = Optional.of(max);
  }
}
