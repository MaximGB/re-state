(ns maximgb.re-state.registry-test
  (:require [cljs.test :refer [deftest is testing async use-fixtures]]
            [maximgb.re-state.core :as xs :refer [register-interpreter!
                                                 unregister-interpreter!
                                                 has-interpreter?
                                                 id->interpreter
                                                 interpreter!
                                                 machine]]))


(deftest registry-test
  (testing "Interpreter registry lifecycle."
    (let [interpreter (interpreter! (machine {:id :null-machine}))]
      (is (not (has-interpreter? ::null-interpreter)) "::null-interpreter is not registered")
      (register-interpreter! ::null-interpreter interpreter)
      (is (has-interpreter? ::null-interpreter) "::null-interpreter has been registered")
      (is (identical? (id->interpreter ::null-interpreter) interpreter) "Interpreter can be obtained by id")
      (unregister-interpreter! ::null-interpreter)
      (is (not (has-interpreter? ::null-interpreter)) "::null-interpreter has been unregistered"))))


(deftest invalid-registration-test
  (testing "It shouldn't be possible to register something but interpreter"
    (try
      (register-interpreter! {} ::null-interpreter)
      (catch js/Error e
          (is true "Exception cought")))))
