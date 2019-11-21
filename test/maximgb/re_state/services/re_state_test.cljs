(ns maximgb.re-state.services.re-state-test
  (:require [cljs.core.async :as casync]
            [cljs.test :refer [deftest is testing async use-fixtures]]
            [re-frame.core :as rf]
            [maximgb.re-state.core :as xs
             :refer [machine
                     interpreter!
                     interpreter-start!
                     interpreter-send!
                     interpreter->started?
                     interpreter->machine
                     fx-action
                     re-state-service]]
            [maximgb.re-state.protocols :as protocols]
            [maximgb.re-state.services.re-state :as service]))


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
                                                                          [[re-state-service :instance []]]
                                                                          (fn [cofx]
                                                                            (casync/put! c (get-in cofx [re-state-service :instance]))))}}))]
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
                                                    [[re-state-service :spawn! []]]
                                                    (fn [cofx]
                                                      (casync/put! c (get-in cofx [re-state-service :spawn!]))))}})
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
                                                         [[re-state-service :spawn! [slave]]]
                                                         (fn [cofx]
                                                           (casync/put! c (get-in cofx [re-state-service :spawn!]))))}})
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
                                                         [[re-state-service ::slave-1 :spawn! [slave]]
                                                          [re-state-service ::slave-2 :spawn! [slave]]]
                                                         (fn [cofx]
                                                           (casync/put! c [(get-in cofx [::slave-1 :spawn!])
                                                                           (get-in cofx [::slave-2 :spawn!])])))}})
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



(deftest spawn-and-start-wo-payload-test
  (testing "Interpreter spawning and starting with instance and payload"
    (async done
           (let [c (casync/timeout 100)
                 slave (machine {:id :slave-machine
                                 :initial :ready
                                 :states {:ready {:entry :send-payload}}}

                                {:actions {:send-payload (fx-action
                                                          (fn [cofx]
                                                            (casync/put! c ::started)))}})

                 master (machine {:id :master-machine
                                  :initial :ready
                                  :states {:ready {:entry :spawn-and-start}}}

                                 {:actions {:spawn-and-start (fx-action
                                                              [[re-state-service :spawn! [slave]]]
                                                              (fn [cofx]
                                                                (let [slave (get-in cofx [re-state-service :spawn!])]
                                                                  {re-state-service [:start! [slave]]})))}})
                 interpreter (interpreter! master)]
             (casync/go
               (interpreter-start! interpreter)
               (is (= (casync/<! c) ::started) "Slave started")
               (done))))))


(deftest spawn-and-start-with-payload-test
  (testing "Interpreter spawning and starting with instance and payload"
    (async done
           (let [c (casync/timeout 100)
                 slave (machine {:id :slave-machine
                                 :initial :ready
                                 :states {:ready {:entry :send-payload}}}

                                {:actions {:send-payload (fx-action
                                                          (fn [cofx [_ & payload]]
                                                            (casync/put! c payload)))}})

                 master (machine {:id :master-machine
                                  :initial :ready
                                  :states {:ready {:entry :spawn-and-start}}}

                                 {:actions {:spawn-and-start (fx-action
                                                              [[re-state-service :spawn! [slave]]]
                                                              (fn [cofx]
                                                                (let [slave (get-in cofx [re-state-service :spawn!])]
                                                                  {re-state-service [:start! [slave 1 2 3]]})))}})
                 interpreter (interpreter! master)]
             (casync/go
               (interpreter-start! interpreter)
               (is (= (casync/<! c) [1 2 3]) "Slave sent correct payload")
               (done))))))


(deftest spawn-and-start-multiple-machines-wo-payload-test
  (testing "Spawning and starting multile machines without payload"
    (async done
           (let [c (casync/timeout 100)
                 slave (machine {:id :slave-machine
                                 :initial :ready
                                 :states {:ready {:entry :send-payload}}}

                                {:actions {:send-payload (fx-action
                                                          (fn [cofx [_ & payload]]
                                                            (casync/put! c ::started)))}})

                 master (machine {:id :master-machine
                                  :initial :ready
                                  :states {:ready {:entry :spawn-and-start}}}

                                 {:actions {:spawn-and-start (fx-action
                                                              [[re-state-service ::slave-1 :spawn! [slave]]
                                                               [re-state-service ::slave-2 :spawn! [slave]]]
                                                              (fn [cofx]
                                                                (let [slave1 (get-in cofx [::slave-1 :spawn!])
                                                                      slave2 (get-in cofx [::slave-2 :spawn!])]
                                                                  {re-state-service [:start! [slave1]
                                                                                     :start! [slave2]]})))}})
                 interpreter (interpreter! master)]
             (casync/go
               (interpreter-start! interpreter)
               (is (= (casync/<! c) ::started) "Slave1 started")
               (is (= (casync/<! c) ::started) "Slave2 started")
               (done))))))


(deftest spawn-and-start-multiple-machines-with-payload-test
  (testing "Spawning and starting multiple machines with payload"
    (async done
           (let [c (casync/timeout 100)
                 slave (machine {:id :slave-machine
                                 :initial :ready
                                 :states {:ready {:entry :send-payload}}}

                                {:actions {:send-payload (fx-action
                                                          (fn [cofx [_ & payload]]
                                                            (casync/put! c payload)))}})

                 master (machine {:id :master-machine
                                  :initial :ready
                                  :states {:ready {:entry :spawn-and-start}}}

                                 {:actions {:spawn-and-start (fx-action
                                                              [[re-state-service ::slave-1 :spawn! [slave]]
                                                               [re-state-service ::slave-2 :spawn! [slave]]]
                                                              (fn [cofx]
                                                                (let [slave1 (get-in cofx [::slave-1 :spawn!])
                                                                      slave2 (get-in cofx [::slave-2 :spawn!])]
                                                                  {re-state-service [:start! [slave1 1]
                                                                                     :start! [slave2 1 2 3]]})))}})
                 interpreter (interpreter! master)]
             (casync/go
               (interpreter-start! interpreter)
               (is (= (casync/<! c) [1]) "Slave 1 started with correct payload")
               (is (= (casync/<! c) [1 2 3]) "Slave 2 started with correct payload")
               (done))))))


(deftest single-interpreter-registration-unregistration-test
  (testing "Single interpreter registration / unregistration"
    (async done
           (let [c (casync/timeout 100)
                 slave (machine {:id :slave-machine})
                 master (machine {:id :master-machine
                                  :initial :register
                                  :states {:register {:entry :spawn-register-slave
                                                      :on {:next :check-registration}}
                                           :check-registration {:entry :check-registration
                                                                :on {:next :unregister}}
                                           :unregister {:entry :unregister-slave
                                                        :on {:next :check-registration}}
                                           :check-unregistration {:entry :check-unregistration}}}

                                 {:actions {:spawn-register-slave (fx-action
                                                                   [[re-state-service ::slave :spawn! [slave]]]
                                                                   (fn [cofx]
                                                                     (let [slave (get-in cofx [::slave :spawn!])]
                                                                       {re-state-service [:register! [::slave slave]]})))

                                            :check-registration (fx-action
                                                                 [[re-state-service :instance [::slave]]]
                                                                 (fn [cofx]
                                                                   (casync/put! c (or (get-in cofx [re-state-service :instance]) :none))))

                                            :unregister-slave (fx-action
                                                               (fn [cofx]
                                                                 {re-state-service [:unregister! [::slave]]}))}})
                 interpreter (interpreter! master)]
             (casync/go
               (interpreter-start! interpreter)      ;; register
               (interpreter-send! interpreter :next) ;; check
               (is (satisfies? protocols/InterpreterProto (casync/<! c)) "Slave intrepreter registered")
               (interpreter-send! interpreter :next) ;; unregister
               (interpreter-send! interpreter :next) ;; check
               (is (= (casync/<! c) :none) "Slave intrepreter unregistered")
               (done))))))


(deftest multiple-interpreter-registration-unregistration-test
  (testing "Multiple interpreters registration / unregistration"
    (async done
           (let [c (casync/timeout 100)
                 slave (machine {:id :slave-machine})
                 master (machine {:id :master-machine
                                  :initial :register
                                  :states {:register {:entry :spawn-register-slave
                                                      :on {:next :check-registration}}
                                           :check-registration {:entry :check-registration
                                                                :on {:next :unregister}}
                                           :unregister {:entry :unregister-slave
                                                        :on {:next :check-registration}}}}

                                 {:actions {:spawn-register-slave (fx-action
                                                                   [[re-state-service ::slave-1 :spawn! [slave]]
                                                                    [re-state-service ::slave-2 :spawn! [slave]]]
                                                                   (fn [cofx]
                                                                     (let [slave-1 (get-in cofx [::slave-1 :spawn!])
                                                                           slave-2 (get-in cofx [::slave-2 :spawn!])]
                                                                       {re-state-service [:register! [::slave-1 slave-1]
                                                                                          :register! [::slave-2 slave-2]]})))

                                            :check-registration (fx-action
                                                                 [[re-state-service ::slave-1 :instance [::slave-1]]
                                                                  [re-state-service ::slave-2 :instance [::slave-2]]]
                                                                 (fn [cofx]
                                                                   (casync/put! c (or (get-in cofx [::slave-1 :instance]) :none))
                                                                   (casync/put! c (or (get-in cofx [::slave-2 :instance]) :none))))

                                            :unregister-slave (fx-action
                                                               (fn [cofx]
                                                                 {re-state-service [:unregister! [::slave-1]
                                                                                    :unregister! [::slave-2]]}))}})
                 interpreter (interpreter! master)]
             (casync/go
               (interpreter-start! interpreter)      ;; register
               (interpreter-send! interpreter :next) ;; check
               (is (satisfies? protocols/InterpreterProto (casync/<! c)) "Slave 1 interpreter registred")
               (is (satisfies? protocols/InterpreterProto (casync/<! c)) "Slave 2 interpreter registred")
               (interpreter-send! interpreter :next) ;; unregister
               (interpreter-send! interpreter :next) ;; check
               (is (= (casync/<! c) :none) "Slave 1 interpreter unregistred")
               (is (= (casync/<! c) :none) "Slave 2 interpreter unregistred")
               (done))))))


(deftest single-interpreter-stop-test
  (testing "Single interpreter stoping"
    (async done
           (let [c (casync/timeout 100)
                 slave (machine {:id :slave-machine
                                 :initial :ready
                                 :states {:ready {}}})

                 master (machine {:id :master-machine
                                  :initial :ready
                                  :states {:ready {:entry :spawn-start-register
                                                   :on {:next :check-started}}
                                           :check-started {:entry :check-started
                                                           :on {:next :stop}}
                                           :stop {:entry :stop
                                                  :on {:next :check-stopped}}
                                           :check-stopped {:entry :check-stopped}}}

                                 {:actions {:spawn-start-register (fx-action
                                                                   [[re-state-service :spawn! [slave]]]
                                                                   (fn [cofx]
                                                                     (let [slave (get-in cofx [re-state-service :spawn!])]
                                                                       {re-state-service [:register! [::slave slave]
                                                                                          :start!    [::slave]]})))

                                            :check-started (fx-action
                                                            (fn [cofx]
                                                              (casync/put! c (interpreter->started? (service/id->interpreter ::slave)))))

                                            :stop (fx-action
                                                   (fn [cofx]
                                                     {re-state-service [:stop! [::slave]]}))

                                            :check-stopped (fx-action
                                                            (fn [cofx]
                                                              (casync/put! c (interpreter->started? (service/id->interpreter ::slave)))))}})
                 interpreter (interpreter! master)]
             (casync/go
               (interpreter-start! interpreter)
               (interpreter-send! interpreter :next) ;; To check
               (is (= (casync/<! c) true) "Slave started")
               (interpreter-send! interpreter :next) ;; Stop
               (interpreter-send! interpreter :next) ;; To check
               (is (= (casync/<! c) false) "Slave stopped")
               (done))))))


(deftest multiple-interpreters-stop-test
  (testing "Multiple interpreters stoping"
    (async done
           (let [c (casync/timeout 100)
                 slave (machine {:id :slave-machine
                                 :initial :ready
                                 :states {:ready {}}})

                 master (machine {:id :master-machine
                                  :initial :ready
                                  :states {:ready {:entry :spawn-start-register
                                                   :on {:next :check-started}}
                                           :check-started {:entry :check-started
                                                           :on {:next :stop}}
                                           :stop {:entry :stop
                                                  :on {:next :check-stopped}}
                                           :check-stopped {:entry :check-stopped}}}

                                 {:actions {:spawn-start-register (fx-action
                                                                   [[re-state-service ::slave-1 :spawn! [slave]]
                                                                    [re-state-service ::slave-2 :spawn! [slave]]]
                                                                   (fn [cofx]
                                                                     (let [slave-1 (get-in cofx [::slave-1 :spawn!])
                                                                           slave-2 (get-in cofx [::slave-2 :spawn!])]
                                                                       {re-state-service [:register! [::slave-1 slave-1]
                                                                                          :start!    [::slave-1]
                                                                                          :register! [::slave-2 slave-2]
                                                                                          :start!    [::slave-2]]})))

                                            :check-started (fx-action
                                                            (fn [cofx]
                                                              (casync/put! c {::slave-1 (interpreter->started? (service/id->interpreter ::slave-1))
                                                                              ::slave-2 (interpreter->started? (service/id->interpreter ::slave-2))})))

                                            :stop (fx-action
                                                   (fn [cofx]
                                                     {re-state-service [:stop! [::slave-1]
                                                                        :stop! [::slave-2]]}))

                                            :check-stopped (fx-action
                                                            (fn [cofx]
                                                              (casync/put! c {::slave-1 (interpreter->started? (service/id->interpreter ::slave-1))
                                                                              ::slave-2 (interpreter->started? (service/id->interpreter ::slave-2))})))}})
                 interpreter (interpreter! master)]
             (casync/go
               (interpreter-start! interpreter)
               (interpreter-send! interpreter :next) ;; To check
               (is (= (casync/<! c) {::slave-1 true
                                     ::slave-2 true}) "Slaves started")
               (interpreter-send! interpreter :next) ;; Stop
               (interpreter-send! interpreter :next) ;; To check
               (is (= (casync/<! c) {::slave-1 false
                                     ::slave-2 false}) "Slaves stopped")
               (done))))))


(deftest single-interpreter-send-test
  (testing "Single interpreter send"
    (async done
           (let [c (casync/timeout 100)
                 slave (machine {:id :slave-machine
                                 :initial :ready
                                 :states {:ready {:on {:report {:actions :put-sent}}}}}

                                {:actions {:put-sent (fx-action
                                                        (fn [cofx [event & payload]]
                                                          (casync/put! c [event payload])))}})

                 master (machine {:id :master-machine
                                  :initial :ready
                                  :states {:ready {:entry :spawn-start-register
                                                   :on {:send-event {:actions :send-event}
                                                        :send-event-payload {:actions :send-event-payload}}}}}

                                 {:actions {:spawn-start-register (fx-action
                                                                   [[re-state-service :spawn! [slave]]]
                                                                   (fn [cofx]
                                                                     (let [slave-1 (get-in cofx [re-state-service :spawn!])]
                                                                       {re-state-service [:register! [::slave slave-1]
                                                                                          :start!    [::slave]]})))

                                            :send-event (fx-action
                                                         (fn [cofx]
                                                           {re-state-service [:send! [::slave :report]]}))

                                            :send-event-payload (fx-action
                                                                 (fn [cofx [event & payload]]
                                                                   {re-state-service [:send! (into [::slave :report] payload)]}))}})
                 interpreter (interpreter! master)]
             (casync/go
               (interpreter-start! interpreter)
               (interpreter-send! interpreter :send-event)
               (is (= (casync/<! c) [:report nil]) "Slave recieved an event w/o payload")
               (interpreter-send! interpreter :send-event-payload 1 2 3)
               (is (= (casync/<! c) [:report [1 2 3]]) "Slave recieved an event with payload")
               (done))))))


(deftest multiple-interpreters-send-test
  (testing "Multiple interpreters send"
    (async done
           (let [c (casync/timeout 100)
                 slave (machine {:id :slave-machine
                                 :initial :ready
                                 :states {:ready {:on {:report {:actions :put-sent}}}}}

                                {:actions {:put-sent (fx-action
                                                      (fn [cofx [event & payload]]
                                                        (casync/put! c [event payload])))}})

                 master (machine {:id :master-machine
                                  :initial :ready
                                  :states {:ready {:entry :spawn-start-register
                                                   :on {:send-event {:actions :send-event}
                                                        :send-event-payload {:actions :send-event-payload}}}}}

                                 {:actions {:spawn-start-register (fx-action
                                                                   [[re-state-service ::slave-1 :spawn! [slave]]
                                                                    [re-state-service ::slave-2 :spawn! [slave]]]
                                                                   (fn [cofx]
                                                                     (let [slave-1 (get-in cofx [::slave-1 :spawn!])
                                                                           slave-2 (get-in cofx [::slave-2 :spawn!])]
                                                                       {re-state-service [:register! [::slave-1 slave-1]
                                                                                          :start!    [::slave-1]
                                                                                          :register! [::slave-2 slave-2]
                                                                                          :start!    [::slave-2]]})))

                                            :send-event (fx-action
                                                         (fn [cofx]
                                                           {re-state-service [:send! [::slave-1 :report]
                                                                              :send! [::slave-2 :report]]}))

                                            :send-event-payload (fx-action
                                                                 (fn [cofx [event & payload]]
                                                                   {re-state-service [:send! (into [::slave-1 :report] payload)
                                                                                      :send! (into [::slave-2 :report] payload)]}))}})
                 interpreter (interpreter! master)]
             (casync/go
               (interpreter-start! interpreter)
               (interpreter-send! interpreter :send-event)
               (is (= (casync/<! c) [:report nil]) "Slave 1 recieved an event w/o payload")
               (is (= (casync/<! c) [:report nil]) "Slave 2 recieved an event w/o payload")
               (interpreter-send! interpreter :send-event-payload 1 2 3)
               (is (= (casync/<! c) [:report [1 2 3]]) "Slave 1 recieved an event with payload")
               (is (= (casync/<! c) [:report [1 2 3]]) "Slave 2 recieved an event with payload")
               (done))))))
