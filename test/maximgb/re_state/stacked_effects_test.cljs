(ns maximgb.re-state.stacked-effects-test
  (:require [cljs.test :refer [deftest is testing async use-fixtures]]
            [cljs.core.async :as casync]
            [re-frame.core :as rf]
            [maximgb.re-state.core :as xs :refer [let-machine
                                                  let-machine->
                                                  def-action-db
                                                  def-action-fx
                                                  def-action-ctx
                                                  interpreter!
                                                  interpreter-start!
                                                  interpreter-send!]]
            [cljs.core.async :as async]))

(def rf-checkpoint (volatile! nil))

(use-fixtures
  :each
  {:before (fn [] (vreset! rf-checkpoint (rf/make-restore-fn)))
   :after (fn [] (@rf-checkpoint))})


(deftest effects-stacking-test
  (testing "Testing applying same effect issued by two or more actions"
    (async done

           (rf/reg-fx
            :apply
            (fn [[fn & args]]
              (apply fn args)))

           (let [c (async/timeout 100)

                 i (interpreter! (let-machine-> {:id       :test-machine
                                                 :initial  :ready
                                                 :states  {:ready {:entry [:action-1 :action-2]}}}

                                   (def-action-fx
                                     :action-1
                                     (fn []
                                       {:apply [async/put! c :action-1]}))

                                   (def-action-fx
                                     :action-2
                                     (fn []
                                       {:apply [async/put! c :action-2]}))))]

             (async/go
               (interpreter-start! i)
               (is (= (async/<! c) :action-1))
               (is (= (async/<! c) :action-2))
               (done))))))
