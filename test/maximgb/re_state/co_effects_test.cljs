(ns maximgb.re-state.co-effects-test
  (:require [cljs.core.async :as casync]
            [cljs.test :refer [deftest is testing async use-fixtures]]
            [re-frame.core :as rf]
            [maximgb.re-state.core :as xs
             :refer [machine
                     interpreter!
                     interpreter-start!
                     interpreter-send!
                     interpreter->machine
                     fx-action
                     cofx-instance
                     cofx-spawn]]
            [maximgb.re-state.protocols :as protocols]))


(def rf-checkpoint (volatile! nil))

(use-fixtures
  :each
  {:before (fn [] (vreset! rf-checkpoint (rf/make-restore-fn)))
   :after (fn [] (@rf-checkpoint))})


(deftest interpreter-inst-cofx-test
  (testing "Interpreter instance co-effect injection"
    (async done
           (let [c (casync/timeout 100)
                 interpreter (interpreter! (machine {:id :simple-machine
                                                     :initial :ready
                                                     :states {:ready {:entry :in-ready}}}

                                                    {:actions {:in-ready (fx-action
                                                                          [cofx-instance]
                                                                          (fn [cofx]
                                                                            (casync/put! c (cofx-instance cofx))))}}))]
             (casync/go
               (interpreter-start! interpreter)
               (is (identical? (casync/<! c) interpreter) "Correct interpreter instance injected")
               (done))))))


(deftest interpreter-spawn-self-test
  (testing "Self interpreter spawning"
    (async done
           (let [c (casync/timeout 100)
                 m (machine {:id :master-machine
                             :initial :ready
                             :states {:ready {:on {:toggle {:target :running}}}
                                      :running {:entry :spawn-self}}}
                            {:actions {:spawn-self (fx-action
                                                    [cofx-spawn]
                                                    (fn [cofx]
                                                      (casync/put! c (cofx-spawn cofx))))}})
                 i1 (interpreter! m)]
             (casync/go
               (interpreter-start! i1)
               (interpreter-send! i1 :toggle)
               (let [i2 (casync/<! c)]
                 (is (satisfies? protocols/InterpreterProto i2) "Interpreter spawned")
                 (is (not (identical? i1 i2)) "Different interpreter spawned")
                 (is (identical? (interpreter->machine i2) m) "Correct interpreter spawned"))
               (done))))))


(deftest interpreter-spawn-single-test
  (testing "Single interpreter spawning"
    (async done
           (let [c (casync/timeout 100)
                 slave (machine {:id :slave-machine})
                 master (machine {:id :master-machine
                                  :initial :ready
                                  :states {:ready {:on {:toggle {:target :running}}}
                                           :running {:entry :spawn-self}}}
                                 {:actions {:spawn-self (fx-action
                                                         [(rf/inject-cofx cofx-spawn slave)]
                                                         (fn [cofx]
                                                           (casync/put! c (cofx-spawn cofx))))}})
                 i1 (interpreter! master)]
             (casync/go
               (interpreter-start! i1)
               (interpreter-send! i1 :toggle)
               (let [i2 (casync/<! c)]
                 (is (satisfies? protocols/InterpreterProto i2) "Interpreter spawned")
                 (is (not (identical? i1 i2)) "Different interpreter spawned")
                 (is (identical? (interpreter->machine i2) slave) "Correct interpreter spawned"))
               (done))))))


(deftest interpreter-spawn-multiple-test
  (testing "Multiple interpreters spawning"
    (async done
           (let [c (casync/timeout 100)
                 slave (machine {:id :slave-machine})
                 master (machine {:id :master-machine
                                  :initial :ready
                                  :states {:ready {:on {:toggle {:target :running}}}
                                           :running {:entry :spawn-self}}}
                                 {:actions {:spawn-self (fx-action
                                                         [(rf/inject-cofx cofx-spawn [slave slave])]
                                                         (fn [cofx]
                                                           (casync/put! c (cofx-spawn cofx))))}})
                 i1 (interpreter! master)]
             (casync/go
               (interpreter-start! i1)
               (interpreter-send! i1 :toggle)
               (let [[i2 i3] (casync/<! c)]
                 (is (satisfies? protocols/InterpreterProto i2) "Second interpreter spawned")
                 (is (satisfies? protocols/InterpreterProto i3) "Third interpreter spawned")
                 (is (not (identical? i1 i2)) "Different second interpreter spawned")
                 (is (not (identical? i2 i3)) "Different third interpreter spawned")
                 (is (identical? (interpreter->machine i2) slave) "Correct second interpreter spawned")
                 (is (identical? (interpreter->machine i3) slave) "Correct third interpreter spawned")))
               (done)))))
