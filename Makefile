-include ~/secrets/.clojars

DEPS_EDN = ./deps.edn
DEV_CLJS_EDN = ./dev.cljs.edn
PACKAGE_JSON = ./package.json
PACKAGE_LOCK = ./package-lock.json
WEBPACK_SRC = ./src/js/webpack.*
XSTATE_BUNDLE = ./target/public/js-out/xstate_bundle.js
JAR = ./target/maximgb.re-state.jar

.PHONY: clean, deploy


all: $(JAR)


clean:
	rm -rf ./target/*


$(PACKAGE_LOCK): $(PACKAGE_JSON)
	npm i


$(XSTATE_BUNDLE): $(PACKAGE_LOCK) $(WEBPACK_SRC)
	npx webpack --config=./src/js/webpack.config.js


$(JAR): $(DEPS_EDN) $(XSTATE_BUNDLE)
	clj -A\:jar


deploy:
	env CLOJARS_USERNAME=${CLOJARS_USERNAME} CLOJARS_PASSWORD=${CLOJARS_PASSWORD} clj -A:deploy
