(ns maximgb.re-state.guards-test
  (:require [cljs.core.async :as casync]
            [cljs.test :refer [deftest is testing async use-fixtures]]
            [maximgb.re-state.core :as xs
                                  :refer [machine
                                          interpreter!
                                          interpreter-start!
                                          interpreter-send!
                                          ev-guard
                                          db-guard
                                          fx-guard
                                          ctx-guard
                                          idb-guard
                                          ifx-guard
                                          ictx-guard]]
            [re-frame.core :as rf]))


(def rf-checkpoint (volatile! nil))

(use-fixtures
  :each
  {:before (fn [] (vreset! rf-checkpoint (rf/make-restore-fn)))
   :after (fn [] (@rf-checkpoint))})


(deftest ev-guard-w-meta-test
  (testing "Event guard with meta data"
    (async done
           (let [c (casync/timeout 100) ;; If something goes wrong we shouldn't wait too long
                 interpreter (interpreter! (machine {:id :simple-machine
                                                     :initial :ready
                                                     :states {:ready {:on {:check [{:cond {:type :filter-age
                                                                                           :valid-age 18
                                                                                           :check :gte}
                                                                                    :actions #(casync/put! c :allow)}
                                                                                   {:cond {:type :filter-age
                                                                                           :valid-age 18
                                                                                           :check :lt}
                                                                                    :actions #(casync/put! c :deny)}]}}}}
                                                    {:guards {:filter-age (ev-guard
                                                                           (fn [_ age & {:keys [valid-age check]}]
                                                                             (cond
                                                                               (= check "lt") (< age valid-age)
                                                                               (= check "gte") (>= age valid-age))))}}))]
             (casync/go
               (interpreter-start! interpreter)
               (interpreter-send! interpreter :check 16)
               (is (= (casync/<! c) :deny) "Underage is not allowed")
               (interpreter-send! interpreter :check 20)
               (is (= (casync/<! c) :allow) "Overage is allowed")
               (done))))))


(deftest db-guard-test
  (testing "DB guard"
    (async done
           (let [c (casync/timeout 100)
                 interpreter (interpreter! (machine {:id :simple-machine
                                                     :initial :ready
                                                     :states {:ready {:on {:toggle {:target :running
                                                                                    :cond :can-run?}}}
                                                              :running {:entry :done}}}

                                                    {:actions {:done #(casync/put! c :done)}
                                                     :guards {:can-run? (db-guard
                                                                         (fn [db [event arg]]
                                                                           (is (= event :toggle) "DB guard has recieved correct event.")
                                                                           (is (= arg :arg) "DB guard has recieved correct event payload.")
                                                                           (::can-run? db)))}}))]
             (rf/reg-event-db
              ::db-guard-test-setup
              (fn [db]
                (assoc db ::can-run? true)))

             (rf/dispatch-sync [::db-guard-test-setup])

             (casync/go
               (interpreter-start! interpreter)
               (interpreter-send! interpreter :toggle :arg)
               (is (= (casync/<! c) :done) "Db guard passed truthy value")
               (done))))))


(deftest idb-guard-test
  (testing "Isolated DB guard"
    (async done
           (let [c (casync/timeout 100)
                 interpreter (interpreter! [::a ::b] (machine {:id :simple-machine
                                                               :initial :ready
                                                               :states {:ready {:on {:toggle {:target :running
                                                                                              :cond :can-run?}}}
                                                                        :running {:entry :done}}}

                                                              {:actions {:done #(casync/put! c :done)}
                                                               :guards {:can-run? (idb-guard
                                                                                   (fn [db [event arg]]
                                                                                     (is (= event :toggle) "DB guard has recieved correct event.")
                                                                                     (is (= arg :arg) "DB guard has recieved correct event payload.")
                                                                                     (::can-run? db)))}}))]
             (rf/reg-event-db
              ::db-guard-test-setup
              (fn [db]
                (assoc-in db [::a ::b ::can-run?] true)))

             (rf/dispatch-sync [::db-guard-test-setup])

             (casync/go
               (interpreter-start! interpreter)
               (interpreter-send! interpreter :toggle :arg)
               (is (= (casync/<! c) :done) "Isolated Db guard passed truthy value")
               (done))))))


(deftest fx-guard-test
  (testing "FX guard")
    (async done
           (let [c (casync/timeout 100) ;; If something goes wrong we shouldn't wait too long
                 interpreter (interpreter! (machine {:id :simple-machine
                                                     :initial :ready
                                                     :states {:ready {:on {:toggle {:target :running
                                                                                    :cond :can-run?}}}
                                                              :running {:entry :done}}}

                                                    {:actions {:done #(casync/put! c :done)}
                                                     :guards {:can-run? (fx-guard
                                                                         (fn [cofx [event arg]]
                                                                           (is (= event :toggle) "Fx-guard has recieved correct event.")
                                                                           (is (= arg :arg) "Fx-guard has recieved correct event payload.")
                                                                           (is (:event cofx) "Fx-handler recieved `cofx` map")
                                                                           (::can-run? (:db cofx))))}}))]
             (rf/reg-event-db
              ::db-guard-test-setup
              (fn [db]
                (assoc db ::can-run? true)))

             (rf/dispatch-sync [::db-guard-test-setup])

             (casync/go
               (interpreter-start! interpreter)
               (interpreter-send! interpreter :toggle :arg)
               (is (= (casync/<! c) :done) "Fx guard passed truthy value")
               (done)))))


(deftest ifx-guard-test
  (testing "Isolated FX guard")
    (async done
           (let [c (casync/timeout 100) ;; If something goes wrong we shouldn't wait too long
                 interpreter (interpreter! [::a ::b] (machine {:id :simple-machine
                                                               :initial :ready
                                                               :states {:ready {:on {:toggle {:target :running
                                                                                              :cond :can-run?}}}
                                                                        :running {:entry :done}}}

                                                              {:actions {:done #(casync/put! c :done)}
                                                               :guards {:can-run? (ifx-guard
                                                                                   (fn [cofx [event arg]]
                                                                                     (is (= event :toggle) "Fx-guard has recieved correct event.")
                                                                                     (is (= arg :arg) "Fx-guard has recieved correct event payload.")
                                                                                     (is (:event cofx) "Fx-handler recieved `cofx` map")
                                                                                     (::can-run? (:db cofx))))}}))]
             (rf/reg-event-db
              ::db-guard-test-setup
              (fn [db]
                (assoc-in db [::a ::b ::can-run?] true)))

             (rf/dispatch-sync [::db-guard-test-setup])

             (casync/go
               (interpreter-start! interpreter)
               (interpreter-send! interpreter :toggle :arg)
               (is (= (casync/<! c) :done) "Isolated Fx guard passed truthy value")
               (done)))))


(deftest ctx-guard-test
  (testing "Ctx guard")
    (async done
           (let [c (casync/timeout 100) ;; If something goes wrong we shouldn't wait too long
                 interpreter (interpreter! (machine {:id :simple-machine
                                                     :initial :ready
                                                     :states {:ready {:on {:toggle {:target :running
                                                                                    :cond :can-run?}}}
                                                              :running {:entry :done}}}

                                                    {:actions {:done #(casync/put! c :done)}
                                                     :guards {:can-run? (ctx-guard
                                                                         (fn [re-ctx]
                                                                           (let [db (rf/get-coeffect re-ctx :db)]
                                                                             (::can-run? db))))}}))]
             (rf/reg-event-db
              ::db-guard-test-setup
              (fn [db]
                (assoc db ::can-run? true)))

             (rf/dispatch-sync [::db-guard-test-setup])

             (casync/go
               (interpreter-start! interpreter)
               (interpreter-send! interpreter :toggle :arg)
               (is (= (casync/<! c) :done) "Ctx guard passed truthy value")
               (done)))))


(deftest ictx-guard-test
  (testing "Isolated CTX guard")
    (async done
           (let [c (casync/timeout 100) ;; If something goes wrong we shouldn't wait too long
                 interpreter (interpreter! [::a ::b] (machine {:id :simple-machine
                                                               :initial :ready
                                                               :states {:ready {:on {:toggle {:target :running
                                                                                              :cond :can-run?}}}
                                                                        :running {:entry :done}}}

                                                              {:actions {:done #(casync/put! c :done)}
                                                               :guards {:can-run? (ictx-guard
                                                                                   (fn [re-ctx]
                                                                                     (let [db (rf/get-coeffect re-ctx :db)]
                                                                                       (::can-run? db))))}}))]
             (rf/reg-event-db
              ::db-guard-test-setup
              (fn [db]
                (assoc-in db [::a ::b ::can-run?] true)))

             (rf/dispatch-sync [::db-guard-test-setup])

             (casync/go
               (interpreter-start! interpreter)
               (interpreter-send! interpreter :toggle :arg)
               (is (= (casync/<! c) :done) "Isolated CTX guard passed truthy value")
               (done)))))


(deftest guards-metadata-test
  (testing "Guards should recieve metadata as keyword arguments"
    (async done
           (let [c (casync/timeout 100) ;; If something goes wrong we shouldn't wait too long
                 interpreter (interpreter! (machine {:id :simple-machine
                                                     :initial :ready
                                                     :states {:ready {:on {:next {:target :one
                                                                                  :cond {:type :ev-guard
                                                                                         :meta :one}}}}
                                                              :one {:on {:next {:target :two
                                                                                :cond {:type :db-guard
                                                                                       :meta :two}}}}
                                                              :two {:on {:next {:target :three
                                                                                :cond {:type :fx-guard
                                                                                       :meta :three}}}}
                                                              :three {:on {:next {:target :four
                                                                                  :cond {:type :ctx-guard
                                                                                         :meta :four}}}}
                                                              :four {:on {:next {:target :five
                                                                                 :cond {:type :idb-guard
                                                                                        :meta :five}}}}
                                                              :five {:on {:next {:target :six
                                                                                 :cond {:type :ifx-guard
                                                                                        :meta :six}}}}
                                                              :six {:on {:next {:target :seven
                                                                                :cond {:type :ictx-guard
                                                                                       :meta :seven}}}}
                                                              :seven {}}}

                                                    {:guards {:ev-guard (ev-guard
                                                                         (fn [_ & {:keys [meta]}]
                                                                           (casync/put! c meta)
                                                                           true))
                                                              :db-guard (db-guard
                                                                         (fn [_ _ & {:keys [meta]}]
                                                                           (casync/put! c meta)
                                                                           true))
                                                              :fx-guard (fx-guard
                                                                         (fn [_ _ & {:keys [meta]}]
                                                                           (casync/put! c meta)
                                                                           true))
                                                              :ctx-guard (ctx-guard
                                                                          (fn [_ _ & {:keys [meta]}]
                                                                            (casync/put! c meta)
                                                                            true))
                                                              :idb-guard (idb-guard
                                                                          (fn [_ _ & {:keys [meta]}]
                                                                            (casync/put! c meta)
                                                                            true))
                                                              :ifx-guard (ifx-guard
                                                                          (fn [_ _ & {:keys [meta]}]
                                                                            (casync/put! c meta)
                                                                            true))
                                                              :ictx-guard (ictx-guard
                                                                           (fn [_ _ & {:keys [meta]}]
                                                                             (casync/put! c meta)
                                                                             true))}}))]
             (casync/go
               (interpreter-start! interpreter)
               (interpreter-send! interpreter :next)
               (is (= (casync/<! c) "one") "EV guard recieved correct meta")
               (interpreter-send! interpreter :next)
               (is (= (casync/<! c) "two") "DB guard recieved correct meta")
               (interpreter-send! interpreter :next)
               (is (= (casync/<! c) "three") "FX guard recieved correct meta")
               (interpreter-send! interpreter :next)
               (is (= (casync/<! c) "four") "CTX guard recieved correct meta")
               (interpreter-send! interpreter :next)
               (is (= (casync/<! c) "five") "IDB guard recieved correct meta")
               (interpreter-send! interpreter :next)
               (is (= (casync/<! c) "six") "IFX guard recieved correct meta")
               (interpreter-send! interpreter :next)
               (is (= (casync/<! c) "seven") "ICTX guard recieved correct meta")
               (done))))))
