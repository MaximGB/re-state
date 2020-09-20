(ns maximgb.re-state.subscriptions-test
  (:require [cljs.test :refer [deftest is testing async use-fixtures]]
            [cljs.core.async :as casync]
            [re-frame.core :as rf]
            [maximgb.re-state.core :refer [machine
                                          interpreter!
                                          interpreter-start!
                                          interpreter-send!
                                          reg-isub
                                          isubscribe
                                          isubscribe-state
                                          idb-action]]
            [maximgb.re-state.utils :as utils]))

(def rf-checkpoint (volatile! nil))

(use-fixtures
  :each
  {:before (fn [] (vreset! rf-checkpoint (rf/make-restore-fn)))
   :after (fn [] (@rf-checkpoint))})


(deftest intrepreter-db-subscription-isolation-test
  (testing "Subscribing to an isolated by interpreter app db part"
    (async done
           (let [c (casync/timeout 100)
                 m (machine {:id :test-machine})
                 i (interpreter! [:a :b :c] m)]

             (rf/reg-event-db
              ::update-db
              (fn [db [_ path value]]
                (assoc-in db path value)))

             (rf/reg-event-db
              ::next
              (fn [db]
                (casync/put! c ::next)
                db))

             (reg-isub
              ::my-sub
              identity)

             (casync/go
               ;; Resetting db
               (rf/dispatch [::update-db nil {}])
               ;; Updating db part under interpreter part manually
               (rf/dispatch [::update-db (utils/interpreter->isolated-db-path i) ::my-value])
               ;; Waiting for dispatch to be handled
               (rf/dispatch [::next])
               (casync/<! c)
               ;; Checking
               (is (= @(rf/subscribe [::my-sub i]) ::my-value) "Subscription returned isolated app db part 1")
               (is (= @(isubscribe i) ::my-value) "Subscription returned isolated app db part 2")
               (done))))))


(deftest isubscribe-state-test
  (testing "Subscription to an interpreter state"
    (async done
           (let [c (casync/timeout 100)
                 m (machine {:id :test-machine
                             :initial :ready
                             :states {:ready {:entry #(casync/put! c :next)
                                              :on {:run :running}}
                                      :running {:entry #(casync/put! c :next)
                                                :on {:stop :ready}}}})
                 i (interpreter! m)
                 s (isubscribe-state i)]
             (casync/go
               (interpreter-start! i)
               (is (nil? @s) "State is unknown before interpreter entered initial state")
               (casync/<! c) ;; waiting for state entry
               (is (= @s :ready) "State subscription reported initial state")
               (interpreter-send! i :run)
               (casync/<! c) ;; waiting for state entry
               (is (= @s :running) "State subscription reported running state")
               (done))))))
