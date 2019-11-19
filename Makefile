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

EXAMPLE_HTML_TPL = ./resources/example-index-tpl.html

EXAMPLE_BASIC = ./docs/examples/basic
EXAMPLE_BASIC_HTML_TITLE = "maximgb.re-state basic example"
EXAMPLE_BASIC_INDEX = $(EXAMPLE_BASIC)/index.html
EXAMPLE_BASIC_CLJS = ./examples/src/maximgb/re_state/example/basic.cljs

EXAMPLE_ACTIONS = ./docs/examples/actions
EXAMPLE_ACTIONS_HTML_TITLE = "maximgb.re-state transition actions example"
EXAMPLE_ACTIONS_INDEX = $(EXAMPLE_ACTIONS)/index.html
EXAMPLE_ACTIONS_CLJS = ./examples/src/maximgb/re_state/example/actions.cljs

EXAMPLE_ENTRYEXIT = ./docs/examples/entryexit
EXAMPLE_ENTRYEXIT_HTML_TITLE = "maximgb.re-state entry/exit actions example"
EXAMPLE_ENTRYEXIT_INDEX = $(EXAMPLE_ENTRYEXIT)/index.html
EXAMPLE_ENTRYEXIT_CLJS = ./examples/src/maximgb/re_state/example/entryexit.cljs

EXAMPLE_GAUGE = ./docs/examples/gauge
EXAMPLE_GAUGE_HTML_TITLE = "maximgb.re-state gauge widget example"
EXAMPLE_GAUGE_INDEX = $(EXAMPLE_GAUGE)/index.html
EXAMPLE_GAUGE_CLJS = ./examples/src/maximgb/re_state/example/gauge.cljs

.PHONY: clean pom deploy xstate_bundle examples


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


$(EXAMPLE_BASIC_INDEX): $(EXAMPLE_HTML_TPL)
	mkdir -p $(EXAMPLE_BASIC)
	cp -f $(EXAMPLE_HTML_TPL) $(EXAMPLE_BASIC_INDEX)


$(EXAMPLE_BASIC): xstate_bundle $(EXAMPLE_BASIC_CLJS) $(EXAMPLE_BASIC_INDEX)
	export EXAMPLE_TITLE="$$(echo $(EXAMPLE_BASIC_HTML_TITLE) | sed -e 's/[\/&]/\\&/g')"; \
  export EXAMPLE_CLJS="$$(echo $(EXAMPLE_BASIC_CLJS) | sed -e 's/[\/&]/\\&/g')"; \
	sed -i -E -z -e "s/%TITLE%/$$EXAMPLE_TITLE/g" -e "s/%EXAMPLE_CLJS%/$$EXAMPLE_CLJS/g" $(EXAMPLE_BASIC_INDEX)
	clj -A\:fig:examples:example-basic


$(EXAMPLE_ACTIONS_INDEX): $(EXAMPLE_HTML_TPL)
	mkdir -p $(EXAMPLE_ACTIONS)
	cp -f $(EXAMPLE_HTML_TPL) $(EXAMPLE_ACTIONS_INDEX)


$(EXAMPLE_ACTIONS): xstate_bundle $(EXAMPLE_ACITONS_CLJS) $(EXAMPLE_ACTIONS_INDEX)
	export EXAMPLE_TITLE="$$(echo $(EXAMPLE_ACTIONS_HTML_TITLE) | sed -e 's/[\/&]/\\&/g')"; \
  export EXAMPLE_CLJS="$$(echo $(EXAMPLE_ACTIONS_CLJS) | sed -e 's/[\/&]/\\&/g')"; \
	sed -i -E -z -e "s/%TITLE%/$$EXAMPLE_TITLE/g" -e "s/%EXAMPLE_CLJS%/$$EXAMPLE_CLJS/g" $(EXAMPLE_ACTIONS_INDEX)
	clj -A\:fig:examples:example-actions


$(EXAMPLE_ENTRYEXIT_INDEX): $(EXAMPLE_HTML_TPL)
	mkdir -p $(EXAMPLE_ENTRYEXIT)
	cp -f $(EXAMPLE_HTML_TPL) $(EXAMPLE_ENTRYEXIT_INDEX)


$(EXAMPLE_ENTRYEXIT): xstate_bundle $(EXAMPLE_ENTRYEXIT_CLJS) $(EXAMPLE_ENTRYEXIT_INDEX)
	export EXAMPLE_TITLE="$$(echo $(EXAMPLE_ENTRYEXIT_HTML_TITLE) | sed -e 's/[\/&]/\\&/g')"; \
  export EXAMPLE_CLJS="$$(echo $(EXAMPLE_ENTRYEXIT_CLJS) | sed -e 's/[\/&]/\\&/g')"; \
	sed -i -E -z -e "s/%TITLE%/$$EXAMPLE_TITLE/g" -e "s/%EXAMPLE_CLJS%/$$EXAMPLE_CLJS/g" $(EXAMPLE_ENTRYEXIT_INDEX)
	clj -A\:fig:examples:example-entry-exit


$(EXAMPLE_GAUGE_INDEX): $(EXAMPLE_HTML_TPL)
	mkdir -p $(EXAMPLE_GAUGE)
	cp -f $(EXAMPLE_HTML_TPL) $(EXAMPLE_GAUGE_INDEX)


$(EXAMPLE_GAUGE): xstate_bundle $(EXAMPLE_GAUGE_CLJS) $(EXAMPLE_GAUGE_INDEX)
	export EXAMPLE_TITLE="$$(echo $(EXAMPLE_GAUGE_HTML_TITLE) | sed -e 's/[\/&]/\\&/g')"; \
  export EXAMPLE_CLJS="$$(echo $(EXAMPLE_GAUGE_CLJS) | sed -e 's/[\/&]/\\&/g')"; \
	sed -i -E -z -e "s/%TITLE%/$$EXAMPLE_TITLE/g" -e "s/%EXAMPLE_CLJS%/$$EXAMPLE_CLJS/g" $(EXAMPLE_GAUGE_INDEX)
	clj -A\:fig:examples:example-gauge


examples: $(EXAMPLE_BASIC) $(EXAMPLE_ACTIONS) $(EXAMPLE_ENTRYEXIT) $(EXAMPLE_GAUGE)


$(JAR): $(POM_XML) xstate_bundle
	clj -A\:jar


deploy:
	env CLOJARS_USERNAME=${CLOJARS_USERNAME} CLOJARS_PASSWORD=${CLOJARS_PASSWORD} clj -A:deploy
