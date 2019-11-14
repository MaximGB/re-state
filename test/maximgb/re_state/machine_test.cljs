(ns maximgb.re-state.machine-test
  (:require [cljs.test :refer [deftest is testing async use-fixtures]]
            [maximgb.re-state.core :refer [machine
                                          machine->config
                                          machine->options
                                          machine->interceptors
                                          machine->xs-machine
                                          machine!
                                          machine-add-action!
                                          machine-add-guard!]]
            [xstate :as jsxs]))


(def test-machine-config-1 {:id :test-1
                            :initial :ready
                            :states {:ready {:on {:go :one
                                                  :check :ready}}
                                     :one {:on {:go-next :two}
                                           :states {:two {:on {:go-self :two
                                                               :stop :ready}}}}}})


(def test-machine-options-1 {:actions {:do #()}})


(deftest machine-creation-test

  (testing "Default machine options should be empty map"
    (let [m (machine test-machine-config-1)]
      (is (= (machine->options m) {}) "Default machine options are correct")))

  (testing "Machine config and options should be stored unchanged"
    (let [m (machine test-machine-config-1 test-machine-options-1)]
      (is (= (machine->config m) test-machine-config-1) "Machine config got unchanged")
      (is (= (machine->options m) test-machine-options-1) "Machine options got unchanged"))))


(deftest xstate-machine-creation-test

  (testing "Machine record should hold instance of XState machine"
    (let [m (machine {})]
      (is (instance? jsxs/StateNode (machine->xs-machine m))))))


(deftest referential-machine-options-modification-test

  (testing "Referential machine options updating"

    (let [*m (machine! test-machine-config-1)
          guard #()
          action #()]
      (machine-add-guard! *m :guard guard)
      (machine-add-action! *m :action action)
      (is (= (machine->options *m)
             {:guards {:guard guard}
              :actions {:action action}})
          "Referential machine options updated correctly"))))
