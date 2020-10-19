(ns maximgb.re-state.shared-db-test
  (:require [cljs.test :refer [deftest is testing async use-fixtures]]
            [cljs.core.async :as casync]
            [re-frame.core :as rf]
            [maximgb.re-state.core :refer [machine
                                           interpreter!
                                           interpreter-start!
                                           interpreter-send!
                                           isubscribe-state
                                           def-action-idb
                                           def-action-ifx
                                           let-machine->]
             :include-macros true]
            [cljs.core.async :as async]))


(def rf-checkpoint (volatile! nil))


(use-fixtures
  :each
  {:before (fn [] (vreset! rf-checkpoint (rf/make-restore-fn)))
   :after (fn [] (@rf-checkpoint))})


(deftest shared-db-test
  (testing "Ability of multiper interpreters to share same isolated re-frame db part"
    (async done
           (let [c  (casync/timeout 100)

                 m1 (let-machine-> {:id :machine-1
                                    :initial :ready-1
                                    :states {:ready-1 {:entry [:store-a :notify-c]}}}
                      (def-action-idb
                        :store-a
                        (fn [db]
                          (assoc db :a :a)))

                      (def-action-ifx
                        :notify-c
                        (fn []
                          (async/put! c :ready-1))))

                 m2 (let-machine-> {:id :machine-2
                                    :initial :ready-2
                                    :states {:ready-2 {:entry [:store-b :notify-c]}}}
                      (def-action-idb
                        :store-b
                        (fn [db]
                          (assoc db :b :b)))

                      (def-action-ifx
                        :notify-c
                        (fn []
                          (async/put! c :ready-2))))

                 i1 (interpreter! [] m1)
                 i2 (interpreter! [] m2)

                 s1 (isubscribe-state i1)
                 s2 (isubscribe-state i2)]
             (interpreter-start! i1)
             (interpreter-start! i2)

             (async/go
               (async/<! c)
               (async/<! c)
               (is (= @s1 :ready-1))
               (is (= @s2 :ready-2))
               (done))))))
