SAMPLE_DIR = $(realpath $(lastword $(MAKEFILE_LIST)))

sample-js-module.js: sample-js-exports.js sample-js-symbols.js sample-wasm.wasm
	$(SAMPLE_DIR)/../../bin/js-ld -o sample-js-module.js -e sample-js-exports.js -s sample-js-symbols.js sample-wasm.wasm
