(ns maximgb.re-state.actions-test
  (:require [cljs.core.async :as casync]
            [cljs.test :refer [deftest is testing async use-fixtures]]
            [maximgb.re-state.core :as xs
                                  :refer [interpreter!
                                          interpreter-start!
                                          interpreter-send!
                                          interpreter->started?
                                          machine
                                          db-action
                                          fx-action
                                          ctx-action
                                          idb-action
                                          ifx-action
                                          ictx-action]]
            [re-frame.core :as rf]))


(def rf-checkpoint (volatile! nil))

(use-fixtures
  :each
  {:before (fn [] (vreset! rf-checkpoint (rf/make-restore-fn)))
   :after (fn [] (@rf-checkpoint))})


(deftest db-action-test
  (testing "DB action co-effect/effect"
    (async done

           (rf/reg-event-db
            ::db-action-test-setup
            (fn [db]
              (assoc db ::db-action-test-key 1)))

           (rf/dispatch-sync [::db-action-test-setup])

           (let [c (casync/timeout 100) ;; If something goes wrong we shouldn't wait too long
                 interpreter (interpreter! (machine {:id :simple-machine
                                                     :initial :ready
                                                     :states {:ready {:entry :in-ready
                                                                      :on {:toggle :running}}
                                                              :running {:entry :in-running}}}

                                                    {:actions {:in-ready (db-action
                                                                          (fn [db [event & payload]]
                                                                            (is (= (event :tetrisrf.xstate/xs-init)) "Initialization event is correct")
                                                                            (is (= (count payload) 0) "Initialization event has no payload")
                                                                            (casync/put! c (::db-action-test-key db))
                                                                            (assoc db ::db-action-test-key 2)))
                                                               :in-running (db-action
                                                                            (fn [db [event arg]]
                                                                              (is (= (event :toggle)) "Transtion event is correct")
                                                                              (is (= arg :arg) "Transition event argument is correct")
                                                                              (casync/put! c (::db-action-test-key db))
                                                                              (assoc db ::db-action-test-key 1)))}}))]
             (casync/go
               (interpreter-start! interpreter)
               (is (= (casync/<! c) 1) "Got correct test key value from db-action handler")
               (interpreter-send! interpreter :toggle :arg)
               (is (= (casync/<! c) 2) "Got updated test-key value from db-action handler")
               (done))))))


(deftest fx-action-test
  (testing "Fx action co-effect/effect"
    (async done

           (let [c (casync/timeout 100) ;; If something goes wrong we shouldn't wait too long
                 interpreter (interpreter! (machine {:id :simple-machine
                                                     :initial :ready
                                                     :states {:ready {:entry :in-ready}}}

                                                    {:actions {:in-ready (fx-action
                                                                          (fn [cofx [event & payload]]
                                                                            (is (= (event :tetrisrf.xstate/xs-init)) "Initialization event is correct")
                                                                            (is (= (count payload) 0) "Initialization event has no payload")
                                                                            (is (:event cofx) "Fx-handler recieved `cofx` map")
                                                                            {::async.put :done}))}}))]
             (rf/reg-fx
              ::async.put
              (fn [val]
                (casync/put! c val)))

             (casync/go
               (interpreter-start! interpreter)
               (is (= (casync/<! c) :done) "Got correct effect from fx-action handler")
               (done))))))


(deftest ctx-action-test
  (testing "Ctx action co-effect/effect"
    (async done

           (let [c (casync/timeout 100) ;; If something goes wrong we shouldn't wait too long
                 interpreter (interpreter! (machine {:id :simple-machine
                                                     :initial :ready
                                                     :states {:ready {:entry :in-ready}}}

                                                    {:actions {:in-ready (ctx-action
                                                                          (fn [re-ctx]
                                                                            (let [event (rf/get-coeffect re-ctx :event)]
                                                                              (is event "Ctx-handler recieved `context`.")
                                                                              (rf/assoc-effect re-ctx ::async.put :done))))}}))]
             (rf/reg-fx
              ::async.put
              (fn [val]
                (casync/put! c val)))

             (casync/go
               (interpreter-start! interpreter)
               (is (= (casync/<! c) :done) "Got correct effect from ctx-action handler")
               (done))))))


(deftest db-action-interceptors-test
  (testing "DB action interceptors injection"
    (async done
           (let [c (casync/timeout 100) ;; If something goes wrong we shouldn't wait too long
                 interpreter (interpreter! (machine {:id :simple-machine
                                                     :initial :ready
                                                     :states {:ready {:on {:toggle {:target :running
                                                                                    :actions :to-running}}}
                                                              :running {:entry [:in-running :check-coeffects]}}}

                                                    {:actions {:to-running (db-action
                                                                            [::test-coeffect-1 ::test-coeffect-2]
                                                                            identity)
                                                               :in-running (db-action
                                                                            [(rf/inject-cofx ::test-coeffect-3 3)]
                                                                            identity)
                                                               :check-coeffects (fn [re-ctx]
                                                                                  (casync/put! c [(rf/get-coeffect re-ctx ::test-coeffect-1)
                                                                                                  (rf/get-coeffect re-ctx ::test-coeffect-2)
                                                                                                  (rf/get-coeffect re-ctx ::test-coeffect-3)]))}}))]

             (rf/reg-cofx
              ::test-coeffect-1
              (fn [cofx]
                (assoc cofx ::test-coeffect-1 1)))

             (rf/reg-cofx
              ::test-coeffect-2
              (fn [cofx]
                (assoc cofx ::test-coeffect-2 2)))

             (rf/reg-cofx
              ::test-coeffect-3
              (fn [cofx val]
                (assoc cofx ::test-coeffect-3 val)))

             (casync/go
               (interpreter-start! interpreter)
               (interpreter-send! interpreter :toggle)
               (is (= (casync/<! c) [1 2 3]) "All coeffects are injected")
               (done))))))


(deftest fx-action-interceptors-test
  (testing "FX action interceptors injection"
    (async done
           (let [c (casync/timeout 100) ;; If something goes wrong we shouldn't wait too long
                 interpreter (interpreter! (machine {:id :simple-machine
                                                     :initial :ready
                                                     :states {:ready {:on {:toggle {:target :running
                                                                                    :actions :to-running}}}
                                                              :running {:entry [:in-running :check-coeffects]}}}

                                                    {:actions {:to-running (fx-action
                                                                            [::test-coeffect-1 ::test-coeffect-2]
                                                                            (fn []))
                                                               :in-running (fx-action
                                                                            [(rf/inject-cofx ::test-coeffect-3 3)]
                                                                            (fn []))
                                                               :check-coeffects (fn [re-ctx]
                                                                                  (casync/put! c [(rf/get-coeffect re-ctx ::test-coeffect-1)
                                                                                                  (rf/get-coeffect re-ctx ::test-coeffect-2)
                                                                                                  (rf/get-coeffect re-ctx ::test-coeffect-3)]))}}))]

             (rf/reg-cofx
              ::test-coeffect-1
              (fn [cofx]
                (assoc cofx ::test-coeffect-1 1)))

             (rf/reg-cofx
              ::test-coeffect-2
              (fn [cofx]
                (assoc cofx ::test-coeffect-2 2)))

             (rf/reg-cofx
              ::test-coeffect-3
              (fn [cofx val]
                (assoc cofx ::test-coeffect-3 val)))

             (casync/go
               (interpreter-start! interpreter)
               (interpreter-send! interpreter :toggle)
               (is (= (casync/<! c) [1 2 3]) "All coeffects are injected")
               (done))))))


(deftest ctx-action-interceptors-test
  (testing "CTX action interceptors injection"
    (async done
           (let [c (casync/timeout 100) ;; If something goes wrong we shouldn't wait too long
                 interpreter (interpreter! (machine {:id :simple-machine
                                                     :initial :ready
                                                     :states {:ready {:on {:toggle {:target :running
                                                                                    :actions :to-running}}}
                                                              :running {:entry [:in-running :check-coeffects]}}}

                                                    {:actions {:to-running (ctx-action
                                                                            [::test-coeffect-1 ::test-coeffect-2]
                                                                            identity)
                                                               :in-running (ctx-action
                                                                            [(rf/inject-cofx ::test-coeffect-3 3)]
                                                                            identity)
                                                               :check-coeffects (fn [re-ctx]
                                                                                  (casync/put! c [(rf/get-coeffect re-ctx ::test-coeffect-1)
                                                                                                  (rf/get-coeffect re-ctx ::test-coeffect-2)
                                                                                                  (rf/get-coeffect re-ctx ::test-coeffect-3)]))}}))]

             (rf/reg-cofx
              ::test-coeffect-1
              (fn [cofx]
                (assoc cofx ::test-coeffect-1 1)))

             (rf/reg-cofx
              ::test-coeffect-2
              (fn [cofx]
                (assoc cofx ::test-coeffect-2 2)))

             (rf/reg-cofx
              ::test-coeffect-3
              (fn [cofx val]
                (assoc cofx ::test-coeffect-3 val)))

             (casync/go
               (interpreter-start! interpreter)
               (interpreter-send! interpreter :toggle)
               (is (= (casync/<! c) [1 2 3]) "All coeffects are injected")
               (done))))))


(deftest idb-action-test
  (testing "IDB action isolation"
    (async done
           (let [c (casync/timeout 100)
                 m (machine {:id :simple-machine
                             :initial :ready
                             :states {:ready {:entry :in-ready}}}

                            {:actions {:in-ready (idb-action
                                                  (fn [db [_ value]]
                                                    (assoc db :val value)))}})
                 interpreter-1 (interpreter! ::i1 m)
                 interpreter-2 (interpreter! ::i2 m)]

             (rf/reg-event-db
              ::idb-action-test
              (fn [db]
                (casync/put! c db)
                db))

             (casync/go
               (interpreter-start! interpreter-1 1)
               (interpreter-start! interpreter-2 2)
               (rf/dispatch [::idb-action-test])
               (let [app-db (casync/<! c)]
                 (is (and
                      (= (get-in app-db [::i1 :db :val]) 1)
                      (= (get-in app-db [::i2 :db :val]) 2))
                     "IDB action data isolation works correctly"))
               (done))))))


(deftest ifx-action-test
  (testing "IFX action isolation"
    (async done
           (let [c (casync/timeout 100)
                 m (machine {:id :simple-machine
                             :initial :ready
                             :states {:ready {:entry :in-ready}}}

                            {:actions {:in-ready (ifx-action
                                                  (fn [cofx [_ value]]
                                                    (let [db (:db cofx)]
                                                      {:db (assoc db :val value)})))}})
                 interpreter-1 (interpreter! ::i1 m)
                 interpreter-2 (interpreter! ::i2 m)]

             (rf/reg-event-db
              ::idb-action-test
              (fn [db]
                (casync/put! c db)
                db))

             (casync/go
               (interpreter-start! interpreter-1 1)
               (interpreter-start! interpreter-2 2)
               (rf/dispatch [::idb-action-test])
               (let [app-db (casync/<! c)]
                 (is (and
                      (= (get-in app-db [::i1 :db :val]) 1)
                      (= (get-in app-db [::i2 :db :val]) 2))
                     "IFX action data isolation works correctly"))
               (done))))))


(deftest ictx-action-test
  (testing "ICTX action isolation"
    (async done
           (let [c (casync/timeout 100)
                 m (machine {:id :simple-machine
                             :initial :ready
                             :states {:ready {:entry :in-ready}}}

                            {:actions {:in-ready (ictx-action
                                                  (fn [re-ctx [_ value]]
                                                    (let [db (rf/get-coeffect re-ctx :db)]
                                                      (rf/assoc-effect re-ctx
                                                                       :db
                                                                       (assoc db :val value)))))}})
                 interpreter-1 (interpreter! ::i1 m)
                 interpreter-2 (interpreter! ::i2 m)]

             (rf/reg-event-db
              ::idb-action-test
              (fn [db]
                (casync/put! c db)
                db))

             (casync/go
               (interpreter-start! interpreter-1 1)
               (interpreter-start! interpreter-2 2)
               (rf/dispatch [::idb-action-test])
               (let [app-db (casync/<! c)]
                 (is (and
                      (= (get-in app-db [::i1 :db :val]) 1)
                      (= (get-in app-db [::i2 :db :val]) 2))
                     "ICTX action data isolation works correctly"))
               (done))))))


(deftest multiple-actions-db-manipulation-test
  (testing "Multiple actions db manipulation test, results should merge, not overwrite each other"
    (async done
           (let [c (casync/timeout 100)
                 m (machine {:id :simple-machine
                             :initial :ready
                             :states {:ready {:entry [:a1 :a2 :a3 :a4 :a5 :a6]}}}

                            {:actions {:a1 (idb-action
                                            (fn [db]
                                              (assoc db :a1 :a1)))
                                       :a2 (idb-action
                                            (fn [db]
                                              (assoc db :a2 :a2)))
                                       :a3 (ifx-action
                                            (fn [cofx]
                                              (let [db (:db cofx)]
                                                {:db (assoc db :a3 :a3)})))
                                       :a4 (ifx-action
                                            (fn [cofx]
                                              (let [db (:db cofx)]
                                                {:db (assoc db :a4 :a4)})))
                                       :a5 (ictx-action
                                            (fn [re-ctx]
                                              (let [db (rf/get-coeffect re-ctx :db)]
                                                (rf/assoc-effect re-ctx :db (assoc db :a5 :a5)))))
                                       :a6 (ictx-action
                                            (fn [re-ctx]
                                              (let [db (rf/get-coeffect re-ctx :db)]
                                                (rf/assoc-effect re-ctx :db (assoc db :a6 :a6)))))}})
                 i (interpreter! ::a m)]

             (rf/reg-event-db
              ::check
              (fn [db]
                (casync/put! c db)
                db))

             (casync/go
               (interpreter-start! i)
               (rf/dispatch [::check])
               (let [db (casync/<! c)]
                 (is (= (get-in db [::a :db :a1]) :a1) "1st action db changed applied")
                 (is (= (get-in db [::a :db :a2]) :a2) "2nd action db changed applied")
                 (is (= (get-in db [::a :db :a3]) :a3) "3rd action db changed applied")
                 (is (= (get-in db [::a :db :a4]) :a4) "4th action db changed applied")
                 (is (= (get-in db [::a :db :a5]) :a5) "5th action db changed applied")
                 (is (= (get-in db [::a :db :a6]) :a6) "6th action db changed applied"))
               (done))))))
