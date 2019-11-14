(ns maximgb.re-state.dsl-test
  (:require [cljs.test :refer [deftest is testing async use-fixtures]]
            [cljs.core.async :as casync]
            [re-frame.core :as rf]
            [maximgb.re-state.core :as xs
                                  :refer [def-machine
                                          def-action-db
                                          def-action-fx
                                          def-action-ctx
                                          def-guard-db
                                          def-guard-fx
                                          def-guard-ctx
                                          interpreter!
                                          interpreter-start!
                                          interpreter-send!]]))

(def rf-checkpoint (volatile! nil))

(use-fixtures
  :each
  {:before (fn [] (vreset! rf-checkpoint (rf/make-restore-fn)))
   :after (fn [] (@rf-checkpoint))})


(def-machine test-machine {:id :test-machine
                           :initial :ready
                           :states {:ready {:entry :db-action
                                            :exit :fx-action
                                            :on {:toggle {:target :running
                                                          :cond :guard
                                                          :actions :ctx-action}}}
                                    :running {}}})

(def-guard-db test-machine :guard (fn [db] true))

(def-action-db test-machine :db-action (fn [db]
                                         (update db ::test-machine inc)))

(def-action-fx test-machine :fx-action (fn [cofx]
                                         {:db (update (:db cofx)
                                                      ::test-machine inc)}))

(def-action-ctx test-machine :ctx-action (fn [re-ctx]
                                           (let [db (rf/get-coeffect re-ctx :db)
                                                 new-db (update db ::test-machine inc)]
                                             (rf/assoc-effect re-ctx :db new-db))))


(deftest defining-machine-test
  (testing "Machine DSL test"
    (async done

           (let [c (casync/timeout 100) ;; If something goes wrong we shouldn't wait too long
                 interpreter (interpreter! test-machine)]

             (casync/go

               (rf/reg-event-db
                ::check-result
                (fn [db]
                  (casync/put! c (::test-machine db))
                  db))

               (interpreter-start! interpreter)
               (interpreter-send! interpreter :toggle)

               (rf/dispatch [::check-result])

               (is (= (casync/<! c) 3) "All actions have been called.")

               (done))))))
