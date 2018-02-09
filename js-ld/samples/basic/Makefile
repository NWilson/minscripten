SAMPLE_DIR = $(dir $(realpath $(lastword $(MAKEFILE_LIST))))

all:: sample-js-module.js

clean::
	rm -f sample-js-module.js

sample-js-module.js: sample-js-exports.js sample-js-symbols.js sample-wasm.wasm
	$(SAMPLE_DIR)/../../bin/js-ld \
	  --output sample-js-module.js \
	  --exports sample-js-exports.js \
	  --symbols sample-js-symbols.js \
	  --externs sample-js-externs.js \
	  sample-wasm.wasm
