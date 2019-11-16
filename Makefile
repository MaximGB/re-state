-include ~/secrets/.clojars

DEPS_EDN = ./deps.edn
POM_XML = ./pom.xml
DEV_CLJS_EDN = ./dev.cljs.edn
PACKAGE_JSON = ./package.json
PACKAGE_LOCK = ./package-lock.json
WEBPACK_SRC = ./src/js/webpack.*
XSTATE_BUNDLE_DEV = ./target/public/js-out/xstate_bundle.js
XSTATE_BUNDLE_PROD = ./target/public/js-out/xstate_bundle.min.js
EXTERNS_SRC = ./src/js/externs.js
EXTERNS_BUNDLE = ./target/public/js-out/externs.js
JAR = ./target/maximgb.re-state.jar
EXAMPLE_BASIC = ./docs/examples/basic
EXAMPLE_BASIC_CLJS = ./examples/src/maximgb/re_state/example/basic.cljs

.PHONY: clean, pom, deploy, xstate_bundle, examples


all: $(JAR)


clean:
	rm -rf ./target/*


$(POM_XML): $(DEPS_EDN)
	clj -Spom


$(PACKAGE_LOCK): $(PACKAGE_JSON)
	npm i


$(XSTATE_BUNDLE_DEV): $(PACKAGE_LOCK) $(WEBPACK_SRC)
	npx webpack --env=development --config=./src/js/webpack.config.js


$(XSTATE_BUNDLE_PROD): $(PACKAGE_LOCK) $(WEBPACK_SRC)
	npx webpack --env=production --config=./src/js/webpack.config.js


$(EXTERNS_BUNDLE): $(EXTERNS_SRC)
	cp -u $(EXTERNS_SRC) $(EXTERNS_BUNDLE)


xstate_bundle: $(XSTATE_BUNDLE_DEV) $(XSTATE_BUNDLE_PROD) $(EXTERNS_BUNDLE)


$(EXAMPLE_BASIC): xstate_bundle $(EXAMPLE_BASIC_CLJS)
	export EXAMPLE_SOURCE="$$(cat $(EXAMPLE_BASIC_CLJS) | sed -e 's/[\/&]/\\&/g' -e 's/$$/\\/')"; sed -i -E -z -e "s/(<code class=\"clojure\">)(.|\n)*?(<\/code>)/\1 $$EXAMPLE_SOURCE \3/" ./docs/examples/basic/index.html
	#clj -A\:fig:example-basic

examples: $(EXAMPLE_BASIC)


$(JAR): $(POM_XML) xstate_bundle
	clj -A\:jar

deploy:
	env CLOJARS_USERNAME=${CLOJARS_USERNAME} CLOJARS_PASSWORD=${CLOJARS_PASSWORD} clj -A:deploy
