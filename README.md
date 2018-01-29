# minscripten
A tiny WebAssembly toolchain! Compiles and links C to WebAssembly and JavaScript

## Goals

The "minscripten" toolchain consists of:
* Helper scripts to assist in downloading, building, and configuring a WebAssembly build system
* A port of the Musl libc standard library
* A JavaScript linker which finishes the output, by creating a suitable AMD-compatible JavaScript module that wraps the WebAssembly output, allowing the WebAssembly object to be encapsulated by higher-level JavaScript exports.

The aim is to produce a usable, linkable, toolchain with an absolute minimum of code. This is in contrast to the Emscripten project, which includes the kitchen sink and has a huge quantity of legacy support. Minscripten aims to simply provide the shortest and simplest scripts which will pull together existing components (Clang, LLD) and produce a fully-functional and fully-featured toolchain.

Minscripten is able to do this in a remarkably small quantity of code, and should provide a simpler platform for people hoping to add WebAssembly support to their build environment.

## Acknowledgements

Minscripten is inspired by the [http://emscripten.org/](Emscripten) toolchain, created by Alon Zakai of Mozilla. Minscripten is not associated in any way with Emscripten, nor does it contain copies of any Emscripten code. The author of minscripten however owes an immense gratitude for help received over the years from the Emscripten community, and for the investment in the web platform made by Alon (Mozilla), Jukka Jylanki, Jacob Gravelle (Google), Derek Schuff (Google), and many others, first in NaCl, then asm.js, and most recently through the WebAssembly initiative.
