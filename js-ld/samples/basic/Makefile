SAMPLE_DIR = $(dir $(realpath $(lastword $(MAKEFILE_LIST))))

all:: sample-js-module.js

clean::
	rm -f sample-js-module.js

sample-js-module.js: sample-js-exports.js sample-js-symbols.js sample-wasm.wasm
	$(SAMPLE_DIR)/../../bin/js-ld -o sample-js-module.js -e sample-js-exports.js -s sample-js-symbols.js sample-wasm.wasm
