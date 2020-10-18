(ns maximgb.re-state.interpreter-test
  (:require [cljs.test :refer [deftest is testing async use-fixtures]]
            [cljs.core.async :as casync]
            [re-frame.core :as rf]
            [maximgb.re-state.core :as xs :refer [machine
                                                  init-event
                                                  db-action
                                                  fx-action
                                                  interpreter!
                                                  interpreter-start!
                                                  interpreter-stop!
                                                  interpreter-send!
                                                  isubscribe-state]]))

(def rf-checkpoint (volatile! nil))

(use-fixtures
  :each
  {:before (fn [] (vreset! rf-checkpoint (rf/make-restore-fn)))
   :after (fn [] (@rf-checkpoint))})


(deftest simple-machine-interpreter
  (testing "Simple machine interpreter"
    (async done
           (let [c (casync/timeout 100) ;; If something goes wrong we shouldn't wait too long
                 machine-spec {:id :simple-machine
                               :initial :ready
                               :states {:ready {:on {:toggle :running}
                                                :entry :at-ready}
                                        :running {:on {:toggle :ready}
                                                  :entry :at-running}}}
                 machine-opts {:actions {:at-running #(casync/put! c :at-running)
                                         :at-ready #(casync/put! c :at-ready)}}
                 interpreter (interpreter! (machine machine-spec machine-opts))]
             (casync/go
               (interpreter-start! interpreter)
               (is (= :at-ready (casync/<! c)) "Machine initialized at `ready` state")
               (interpreter-send! interpreter :toggle)
               (is (= :at-running (casync/<! c)) "Machine toggled to `running` state")
               (interpreter-send! interpreter :toggle)
               (is (= :at-ready (casync/<! c)) "Machine toggled to `ready` state")
               (done))))))


(deftest multiple-actions-test
  (testing "Multiple actions execution and their order of execution"
    (async done
           (let [c (casync/timeout 100) ;; If something goes wrong we shouldn't wait too long
                 interpreter (interpreter! (machine {:id :simple-machine
                                                     :initial :ready
                                                     :states {:ready {:on {:toggle {:target :running
                                                                                    :actions [:one :two :three]}}}
                                                              :running {}}}
                                                    {:actions {:one #(casync/put! c :one)
                                                               :two #(casync/put! c :two)
                                                               :three #(casync/put! c :three)}}))]
             (casync/go
               (interpreter-start! interpreter)
               (interpreter-send! interpreter :toggle)
               (is (= (casync/<! c) :one) "Action :one executed")
               (is (= (casync/<! c) :two) "Action :two executed")
               (is (= (casync/<! c) :three) "Action :three executed")
               (done))))))


(deftest init-event-payload-test
  (testing "Interpreter start payload passing"
    (async done
           (let [c (casync/timeout 100) ;; If something goes wrong we shouldn't wait too long
                 interpreter (interpreter! (machine {:id :simple-machine
                                                     :initial :ready
                                                     :states {:ready {:entry :in-ready}}}

                                                    {:actions {:in-ready (db-action
                                                                          (fn [db [event & payload]]
                                                                            (casync/put! c event)
                                                                            (casync/put! c payload)
                                                                            db))}}))]
             (casync/go
               (interpreter-start! interpreter :one :two)
               (is (= (casync/<! c) init-event) "Init event recieved")
               (is (= (casync/<! c) [:one :two]) "Init event payload recieved")
               (done))))))


(deftest parallel-states-report-and-transition-test
  (testing "Parallel states transition and reporting"
    (async done
           (let [c (casync/timeout 100)
                 m (machine {:id :parallel-machine
                             :initial :ready
                             :states {:ready {:entry #(casync/put! c ::check)
                                              :type :parallel
                                              :states {:ready-one {:initial :waiting
                                                                   :states {:waiting {:on {:run :running}}
                                                                            :running {:entry #(casync/put! c ::check)}}}
                                                       :ready-two {:initial :waiting
                                                                   :states {:waiting {:on {:run :running}}
                                                                            :running {:entry #(casync/put! c ::check)}}}}}}})
                 i (interpreter! m)
                 s (isubscribe-state i)]
             (casync/go
               (interpreter-start! i)
               (casync/<! c) ;; waiting for check time
               (is (= @s {:ready {:ready-one :waiting
                                  :ready-two :waiting}})
                   "Correct interpreter state reported")
               (interpreter-send! i :run )
               (casync/<! c) ;; waiting for check time from one state
               (casync/<! c) ;; waiting for check time from another state
               (is (= @s {:ready {:ready-one :running
                                  :ready-two :running}})
                   "Correct interpreter transition executed")
               (done))))))


(deftest history-state-test
  (testing "History state support"
    (async done
           (let [c (casync/timeout 100)
                 m (machine {:id :history-machine
                             :initial :ready
                             :states {:ready   {:initial :one
                                                :on      {:run :running}
                                                :states  {:one  {:entry #(casync/put! c :ready.one)
                                                                 :on {:to-two :two}}
                                                          :two  {:entry #(casync/put! c :ready.two)
                                                                 :on {:to-one :one}}
                                                          :hist {:type    :history
                                                                 :history :shallow}}}

                                      :running {:entry #(casync/put! c :running)
                                                :on    {:stop :ready.hist}}}})
                 i (interpreter! m)]
             (casync/go
               (interpreter-start! i)
               (is (= (casync/<! c) :ready.one) "Entered :ready.one state")
               (interpreter-send! i :to-two)
               (is (= (casync/<! c) :ready.two) "Entered :ready.two state")
               (interpreter-send! i :run)
               (is (= (casync/<! c) :running) "Entered :running state")
               (interpreter-send! i :stop)
               (is (= (casync/<! c) :ready.two) "Entered :ready.two via :ready.hist history state")
               (done))))))


(deftest interceptors-given-as-sequences-test
  (testing "Interceptors handling given as sequences or vectors"
    (async done
           (let [c (casync/timeout 100)
                 m (machine {:id      :basic-machine
                             :initial :ready
                             :states {:ready {:entry :on-ready}}}

                            {:actions {:on-ready (fx-action [[::v-interceptor c]]
                                                            (fn [cofx]
                                                              (casync/put! (::v-interceptor cofx) :ok)))}})
                 i (interpreter! m)]

             (rf/reg-cofx
              ::v-interceptor
              (fn [cofx [c]]
                (assoc cofx ::v-interceptor c)))

             (casync/go
               (interpreter-start! i)
               (is (= (casync/<! c) :ok) "Interceptor given as a vector has been called.")
               (done))))))
