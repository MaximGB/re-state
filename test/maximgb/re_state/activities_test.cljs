(ns maximgb.re-state.activities-test
  (:require [cljs.core.async :as casync]
            [cljs.test :refer [deftest is testing async use-fixtures]]
            [maximgb.re-state.core :as xs
                                   :refer [interpreter!
                                           interpreter-start!
                                           interpreter-send!
                                           interpreter->state
                                           machine
                                           ex-activity]]
            [re-frame.core :as rf]))


(def rf-checkpoint (volatile! nil))

(use-fixtures
  :each
  {:before (fn [] (vreset! rf-checkpoint (rf/make-restore-fn)))
   :after (fn [] (@rf-checkpoint))})


(deftest activity-test
  (testing "Activities spawning and stopping"
    (async done

           (let [c (casync/timeout 100) ;; If something goes wrong we shouldn't wait too long
                 interpreter (interpreter! (machine {:id       :simple-machine
                                                     :initial  :ready
                                                     :states  {:ready {:on {:stop :stop}
                                                                       :activities [:beeping]}
                                                               :stop {}}}

                                                    {:activities {:beeping (ex-activity (fn []
                                                                                          (let [*stop (atom nil)]
                                                                                            (casync/go-loop [counter 1]
                                                                                              (when-not @*stop
                                                                                                (casync/put! c (if (mod counter 2)
                                                                                                                 :beep
                                                                                                                 :boop))
                                                                                                (recur (inc counter))))
                                                                                            (fn []
                                                                                              (casync/put! c :stopped)
                                                                                              (swap! *stop nil)))))}}))]

             (interpreter-start! interpreter)


             (casync/go
               (is (= (casync/<! c) :beep))
               (is (= (casync/<! c) :boop))
               (is (= (casync/<! c) :beep))
               (is (= (casync/<! c) :boop))
               (interpreter-send! interpreter :stop)
               (is (= (casync/<! c) :stopped))
               (done))))))
