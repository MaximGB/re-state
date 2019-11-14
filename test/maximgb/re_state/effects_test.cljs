(ns maximgb.re-state.effects-test
  (:require [cljs.core.async :as casync]
            [cljs.test :refer [deftest is testing async use-fixtures]]
            [re-frame.core :as rf]
            [maximgb.re-state.core :as xs
             :refer [machine
                     interpreter!
                     interpreter-start!
                     interpreter-send!
                     interpreter->started?
                     fx-action
                     cofx-instance
                     cofx-spawn
                     fx-register
                     fx-unregister
                     fx-start
                     fx-stop
                     fx-send
                     has-interpreter?
                     id->interpreter]]))


(def rf-checkpoint (volatile! nil))

(use-fixtures
  :each
  {:before (fn [] (vreset! rf-checkpoint (rf/make-restore-fn)))
   :after (fn [] (@rf-checkpoint))})


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
                                                              [(rf/inject-cofx cofx-spawn slave)]
                                                              (fn [cofx]
                                                                (let [slave (cofx-spawn cofx)]
                                                                  {fx-start slave})))}})
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
                                                              [(rf/inject-cofx cofx-spawn slave)]
                                                              (fn [cofx]
                                                                (let [slave (cofx-spawn cofx)]
                                                                  {fx-start [slave 1 2 3]})))}})
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
                                                              [(rf/inject-cofx cofx-spawn [slave slave])]
                                                              (fn [cofx]
                                                                (let [[slave1 slave2] (cofx-spawn cofx)]
                                                                  {fx-start {slave1 nil
                                                                             slave2 nil}})))}})
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
                                                              [(rf/inject-cofx cofx-spawn [slave slave])]
                                                              (fn [cofx]
                                                                (let [[slave1 slave2] (cofx-spawn cofx)]
                                                                  {fx-start {slave1 1
                                                                             slave2 [1 2 3]}})))}})
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
                                                                   [(rf/inject-cofx cofx-spawn slave)]
                                                                   (fn [cofx]
                                                                     (let [slave (cofx-spawn cofx)]
                                                                       {fx-register [::slave slave]})))

                                            :check-registration (fx-action
                                                                 (fn [cofx]
                                                                   (casync/put! c ::check)))

                                            :unregister-slave (fx-action
                                                               (fn [cofx]
                                                                 {fx-unregister ::slave}))}})
                 interpreter (interpreter! master)]
             (casync/go
               (interpreter-start! interpreter)
               (interpreter-send! interpreter :next)
               (casync/<! c) ;; waiting for check ready
               (is (has-interpreter? ::slave) "Slave interpreter registred")
               (interpreter-send! interpreter :next)
               (casync/<! c) ;; waiting for check ready
               (is (not (has-interpreter? ::slave)) "Slave interpreter unregistred")
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
                                                                   [(rf/inject-cofx cofx-spawn [slave slave])]
                                                                   (fn [cofx]
                                                                     (let [[slave-1 slave-2] (cofx-spawn cofx)]
                                                                       {fx-register {::slave-1 slave-1
                                                                                     ::slave-2 slave-2}})))

                                            :check-registration (fx-action
                                                                 (fn [cofx]
                                                                   (casync/put! c ::check)))

                                            :unregister-slave (fx-action
                                                               (fn [cofx]
                                                                 {fx-unregister [::slave-1 ::slave-2]}))}})
                 interpreter (interpreter! master)]
             (casync/go
               (interpreter-start! interpreter)
               (interpreter-send! interpreter :next)
               (casync/<! c) ;; waiting for check ready
               (is (has-interpreter? ::slave-1) "Slave 1 interpreter registred")
               (is (has-interpreter? ::slave-2) "Slave 2 interpreter registred")
               (interpreter-send! interpreter :next)
               (casync/<! c) ;; waiting for check ready
               (is (not (has-interpreter? ::slave-1)) "Slave interpreter unregistred")
               (is (not (has-interpreter? ::slave-2)) "Slave interpreter unregistred")
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
                                                                   [(rf/inject-cofx cofx-spawn slave)]
                                                                   (fn [cofx]
                                                                     (let [slave (cofx-spawn cofx)]
                                                                       {fx-start slave
                                                                        fx-register [::slave slave]})))

                                            :check-started (fx-action
                                                            (fn [cofx]
                                                              (casync/put! c (interpreter->started? (id->interpreter ::slave)))))

                                            :stop (fx-action
                                                   (fn [cofx]
                                                     {fx-stop ::slave}))

                                            :check-stopped (fx-action
                                                            (fn [cofx]
                                                              (casync/put! c (interpreter->started? (id->interpreter ::slave)))))}})
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
                                                                   [(rf/inject-cofx cofx-spawn [slave slave])]
                                                                   (fn [cofx]
                                                                     (let [[slave-1 slave-2] (cofx-spawn cofx)]
                                                                       {fx-start {slave-1 nil
                                                                                  slave-2 nil}
                                                                        fx-register {::slave-1 slave-1
                                                                                     ::slave-2 slave-2}})))

                                            :check-started (fx-action
                                                            (fn [cofx]
                                                              (casync/put! c {::slave-1 (interpreter->started? (id->interpreter ::slave-1))
                                                                              ::slave-2 (interpreter->started? (id->interpreter ::slave-2))})))

                                            :stop (fx-action
                                                   (fn [cofx]
                                                     {fx-stop [::slave-1 ::slave-2]}))

                                            :check-stopped (fx-action
                                                            (fn [cofx]
                                                              (casync/put! c {::slave-1 (interpreter->started? (id->interpreter ::slave-1))
                                                                              ::slave-2 (interpreter->started? (id->interpreter ::slave-2))})))}})
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
                                                                   [(rf/inject-cofx cofx-spawn slave)]
                                                                   (fn [cofx]
                                                                     (let [slave (cofx-spawn cofx)]
                                                                       {fx-start slave
                                                                        fx-register [::slave slave]})))

                                            :send-event (fx-action
                                                         (fn [cofx]
                                                           {fx-send [::slave :report]}))

                                            :send-event-payload (fx-action
                                                                 (fn [cofx [event & payload]]
                                                                   {fx-send (into [::slave :report] payload)}))}})
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
                                                                   [(rf/inject-cofx cofx-spawn [slave slave])]
                                                                   (fn [cofx]
                                                                     (let [[slave-1 slave-2] (cofx-spawn cofx)]
                                                                       {fx-start {slave-1 nil
                                                                                  slave-2 nil}
                                                                        fx-register {::slave-1 slave-1
                                                                                     ::slave-2 slave-2}})))

                                            :send-event (fx-action
                                                         (fn [cofx]
                                                           {fx-send {::slave-1 :report
                                                                     ::slave-2 :report}}))

                                            :send-event-payload (fx-action
                                                                 (fn [cofx [event & payload]]
                                                                   {fx-send {::slave-1 (into [:report] payload)
                                                                             ::slave-2 (into [:report] payload)}}))}})
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
