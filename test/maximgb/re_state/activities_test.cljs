(ns maximgb.re-state.activities-test
  (:require [cljs.core.async :as casync]
            [cljs.test :refer [deftest is testing async use-fixtures]]
            [maximgb.re-state.core :as xs
             :refer [interpreter!
                     interpreter-start!
                     interpreter-send!
                     interpreter->state
                     machine
                     init-event
                     ev-activity
                     db-activity
                     idb-activity
                     fx-activity
                     ifx-activity
                     ctx-activity
                     ictx-activity]]
            [re-frame.core :as rf]
            [maximgb.re-state.utils :as utils]))


(def rf-checkpoint (volatile! nil))

(use-fixtures
  :each
  {:before (fn [] (vreset! rf-checkpoint (rf/make-restore-fn)))
   :after (fn [] (@rf-checkpoint))})


(deftest ev-activity-test
  (testing "ev-activity spawning and stopping"
    (async done

           (let [c (casync/timeout 100) ;; If something goes wrong we shouldn't wait too long
                 interpreter (interpreter! (machine {:id       :simple-machine
                                                     :initial  :ready
                                                     :states  {:ready {:on {:stop :stop}
                                                                       :activities [{:type :beeping :other "data"}]}
                                                               :stop {}}}

                                                    {:activities {:beeping (ev-activity (fn [ev payload & {:as meta}]
                                                                                          (let [*stop (atom nil)]
                                                                                            (casync/go-loop [counter 1]
                                                                                              (let [pause (casync/timeout 10)]
                                                                                                (if-not @*stop
                                                                                                  (do
                                                                                                    (casync/>! c (if (= 1 (mod counter 2))
                                                                                                                   :beep
                                                                                                                   :boop))
                                                                                                    (casync/<! pause)
                                                                                                    (recur (inc counter)))
                                                                                                  (casync/>! c [payload meta]))))
                                                                                            (fn []
                                                                                              (reset! *stop true)))))}}))]

             (interpreter-start! interpreter :payload)


             (casync/go
               (is (= (casync/<! c) :beep))
               (is (= (casync/<! c) :boop))
               (is (= (casync/<! c) :beep))
               (is (= (casync/<! c) :boop))
               (interpreter-send! interpreter :stop)
               (is (= (casync/<! c) [:payload {:id nil :type "beeping" :other "data"}]))
               (done))))))


(deftest db-activity-test
  (testing "db-activity spawning and stopping"
    (async done

           (rf/reg-event-db
            ::db-activity-test-setup
            (fn [db]
              (assoc db ::db-activity-test-key 1)))

           (rf/dispatch-sync [::db-activity-test-setup])

           (let [c (casync/timeout 100) ;; If something goes wrong we shouldn't wait too long
                 interpreter (interpreter! (machine {:id       :simple-machine
                                                     :initial  :ready
                                                     :states  {:ready {:on {:stop :stop}
                                                                       :activities [:beeping]}
                                                               :stop {}}}

                                                    {:activities {:beeping (db-activity (fn [db]
                                                                                          (let [*stop (atom nil)]
                                                                                            (casync/go-loop [counter 1]
                                                                                              (let [pause (casync/timeout 10)]
                                                                                                (if-not @*stop
                                                                                                  (do
                                                                                                    (casync/>! c (if (= 1 (mod counter 2))
                                                                                                                   :beep
                                                                                                                   :boop))
                                                                                                    (casync/<! pause)
                                                                                                    (recur (inc counter)))
                                                                                                  (casync/>! c (::db-activity-test-key db)))))
                                                                                            (fn []
                                                                                              (reset! *stop true)))))}}))]

             (interpreter-start! interpreter)


             (casync/go
               (is (= (casync/<! c) :beep))
               (is (= (casync/<! c) :boop))
               (is (= (casync/<! c) :beep))
               (is (= (casync/<! c) :boop))
               (interpreter-send! interpreter :stop)
               (is (= (casync/<! c) 1))
               (done)))))

  (testing "db-activity params"

    (async done

           (rf/reg-event-db
            ::db-activity-test-setup
            (fn [db]
              (assoc db ::db-activity-test-key 2)))

           (rf/dispatch-sync [::db-activity-test-setup])

           (let [c (casync/timeout 100) ;; If something goes wrong we shouldn't wait too long
                 interpreter (interpreter! (machine {:id       :simple-machine
                                                     :initial  :ready
                                                     :states  {:ready   {:on {:start :beeping}}
                                                               :beeping {:on {:stop :stop}
                                                                         :activities [{:type :beeping :other "data"}]}
                                                               :stop {}}}

                                                    {:activities {:beeping (db-activity (fn [db event & {:as meta}]
                                                                                          (let [*stop (atom nil)]
                                                                                            (casync/go-loop [counter 1]
                                                                                              (let [pause (casync/timeout 10)]
                                                                                                (if-not @*stop
                                                                                                  (do
                                                                                                    (casync/>! c (if (= 1 (mod counter 2))
                                                                                                                   :beep
                                                                                                                   :boop))
                                                                                                    (casync/<! pause)
                                                                                                    (recur (inc counter)))
                                                                                                  (casync/>! c [event meta]))))
                                                                                            (fn []
                                                                                              (reset! *stop true)))))}}))]

             (interpreter-start! interpreter)

             (interpreter-send! interpreter :start 1 2 3)

             (casync/go
               (is (= (casync/<! c) :beep))
               (is (= (casync/<! c) :boop))
               (is (= (casync/<! c) :beep))
               (is (= (casync/<! c) :boop))
               (interpreter-send! interpreter :stop)
               (is (= (casync/<! c) [[:start 1 2 3] {:id nil :type "beeping" :other "data"}]))
               (done))))))


(deftest idb-activity-test
  (testing "idb-activity spawning, stopping and isolation"
    (async done

           (let [c (casync/timeout 100) ;; If something goes wrong we shouldn't wait too long
                 interpreter (interpreter! (machine {:id       :simple-machine
                                                     :initial  :ready
                                                     :states  {:ready {:on {:stop :stop}
                                                                       :activities [:beeping]}
                                                               :stop {}}}

                                                    {:activities {:beeping (idb-activity (fn [db]
                                                                                          (let [*stop (atom nil)]
                                                                                            (casync/go-loop [counter 1]
                                                                                              (let [pause (casync/timeout 10)]
                                                                                                (if-not @*stop
                                                                                                  (do
                                                                                                    (casync/>! c (if (= 1 (mod counter 2))
                                                                                                                   :beep
                                                                                                                   :boop))
                                                                                                    (casync/<! pause)
                                                                                                    (recur (inc counter)))
                                                                                                  (casync/>! c db))))
                                                                                            (fn []
                                                                                              (reset! *stop true)))))}}))]

             (rf/reg-event-db
              ::db-activity-test-setup
              (fn [db]
                (assoc-in db (utils/interpreter->isolated-db-path interpreter) :isolated-value)))

             (rf/dispatch-sync [::db-activity-test-setup])

             (interpreter-start! interpreter)

             (casync/go
               (is (= (casync/<! c) :beep))
               (is (= (casync/<! c) :boop))
               (is (= (casync/<! c) :beep))
               (is (= (casync/<! c) :boop))
               (interpreter-send! interpreter :stop)
               (is (= (casync/<! c) :isolated-value))
               (done))))))


(deftest fx-activity-test
  (testing "fx-activity spawning, stopping and params"
    (async done

           (let [c (casync/timeout 100) ;; If something goes wrong we shouldn't wait too long
                 interpreter (interpreter! (machine {:id       :simple-machine
                                                     :initial  :ready
                                                     :states  {:ready {:on {:stop :stop}
                                                                       :activities [:beeping]}
                                                               :stop {}}}

                                                    {:activities {:beeping (fx-activity (fn [cofx event & {:as meta}]
                                                                                          (let [*stop (atom nil)]
                                                                                            (casync/go-loop [counter 1]
                                                                                              (let [pause (casync/timeout 10)]
                                                                                                (if-not @*stop
                                                                                                  (do
                                                                                                    (casync/>! c (if (= 1 (mod counter 2))
                                                                                                                   :beep
                                                                                                                   :boop))
                                                                                                    (casync/<! pause)
                                                                                                    (recur (inc counter)))
                                                                                                  (casync/>! c [cofx event meta]))))
                                                                                            (fn []
                                                                                              (reset! *stop true)))))}}))]

             (rf/reg-event-db
              ::fx-activity-test-setup
              (fn [db]
                (assoc db :fx-activity :test-value)))

             (rf/dispatch-sync [::fx-activity-test-setup])

             (interpreter-start! interpreter)

             (casync/go
               (is (= (casync/<! c) :beep))
               (is (= (casync/<! c) :boop))
               (is (= (casync/<! c) :beep))
               (is (= (casync/<! c) :boop))
               (interpreter-send! interpreter :stop)
               (let [[{{test-value :fx-activity} :db} event {type :type}] (casync/<! c)]
                 (is (= test-value :test-value))
                 (is (= event [init-event]))
                 (is (= type "beeping")))
               (done))))))


(deftest ifx-activity-test
  (testing "ifx-activity spawning, stopping and params and isolation"
    (async done

           (let [c (casync/timeout 100) ;; If something goes wrong we shouldn't wait too long
                 interpreter (interpreter! (machine {:id       :simple-machine
                                                     :initial  :ready
                                                     :states  {:ready {:on {:stop :stop}
                                                                       :activities [:beeping]}
                                                               :stop {}}}

                                                    {:activities {:beeping (ifx-activity (fn [cofx event & {:as meta}]
                                                                                           (let [*stop (atom nil)]
                                                                                             (casync/go-loop [counter 1]
                                                                                               (let [pause (casync/timeout 10)]
                                                                                                 (if-not @*stop
                                                                                                   (do
                                                                                                     (casync/>! c (if (= 1 (mod counter 2))
                                                                                                                    :beep
                                                                                                                    :boop))
                                                                                                     (casync/<! pause)
                                                                                                     (recur (inc counter)))
                                                                                                   (casync/>! c [cofx event meta]))))
                                                                                             (fn []
                                                                                               (reset! *stop true)))))}}))]
             (rf/reg-event-db
              ::fx-activity-test-setup
              (fn [db]
                (assoc-in db (utils/interpreter->isolated-db-path interpreter) :isolated-value)))

             (rf/dispatch-sync [::fx-activity-test-setup])

             (interpreter-start! interpreter)

             (casync/go
               (is (= (casync/<! c) :beep))
               (is (= (casync/<! c) :boop))
               (is (= (casync/<! c) :beep))
               (is (= (casync/<! c) :boop))
               (interpreter-send! interpreter :stop)
               (let [[{db :db} event {type :type}] (casync/<! c)]
                 (is (= db :isolated-value))
                 (is (= event [init-event]))
                 (is (= type "beeping")))
               (done))))))


(deftest ctx-activity-test
  (testing "ctx-activity spawning, stopping and params"
    (async done

           (let [c (casync/timeout 100) ;; If something goes wrong we shouldn't wait too long
                 interpreter (interpreter! (machine {:id       :simple-machine
                                                     :initial  :ready
                                                     :states  {:ready {:on {:stop :stop}
                                                                       :activities [:beeping]}
                                                               :stop {}}}

                                                    {:activities {:beeping (ctx-activity (fn [re-ctx event & {:as meta}]
                                                                                           (let [*stop (atom nil)]
                                                                                             (casync/go-loop [counter 1]
                                                                                               (let [pause (casync/timeout 10)]
                                                                                                 (if-not @*stop
                                                                                                   (do
                                                                                                     (casync/>! c (if (= 1 (mod counter 2))
                                                                                                                    :beep
                                                                                                                    :boop))
                                                                                                     (casync/<! pause)
                                                                                                     (recur (inc counter)))
                                                                                                   (casync/>! c [re-ctx event meta]))))
                                                                                             (fn []
                                                                                               (reset! *stop true)))))}}))]

             (rf/reg-event-db
              ::ctx-activity-test-setup
              (fn [db]
                (assoc db :ctx-activity :test-value)))

             (rf/dispatch-sync [::ctx-activity-test-setup])

             (interpreter-start! interpreter)

             (casync/go
               (is (= (casync/<! c) :beep))
               (is (= (casync/<! c) :boop))
               (is (= (casync/<! c) :beep))
               (is (= (casync/<! c) :boop))
               (interpreter-send! interpreter :stop)
               (let [[re-ctx event {type :type}] (casync/<! c)
                     db (rf/get-coeffect re-ctx :db)
                     test-value (:ctx-activity db)]
                 (is (= test-value :test-value))
                 (is (= event [init-event]))
                 (is (= type "beeping")))
               (done))))))


(deftest ictx-activity-test
  (testing "ictx-activity spawning, stopping, params and isolation"
    (async done

           (let [c (casync/timeout 100) ;; If something goes wrong we shouldn't wait too long
                 interpreter (interpreter! (machine {:id       :simple-machine
                                                     :initial  :ready
                                                     :states  {:ready {:on {:stop :stop}
                                                                       :activities [:beeping]}
                                                               :stop {}}}

                                                    {:activities {:beeping (ictx-activity (fn [re-ctx event & {:as meta}]
                                                                                            (let [*stop (atom nil)]
                                                                                              (casync/go-loop [counter 1]
                                                                                                (let [pause (casync/timeout 10)]
                                                                                                  (if-not @*stop
                                                                                                    (do
                                                                                                      (casync/>! c (if (= 1 (mod counter 2))
                                                                                                                     :beep
                                                                                                                     :boop))
                                                                                                      (casync/<! pause)
                                                                                                      (recur (inc counter)))
                                                                                                    (casync/>! c [re-ctx event meta]))))
                                                                                              (fn []
                                                                                                (reset! *stop true)))))}}))]

             (rf/reg-event-db
              ::ictx-activity-test-setup
              (fn [db]
                (assoc-in db (utils/interpreter->isolated-db-path interpreter) :isolated-value)))

             (rf/dispatch-sync [::ictx-activity-test-setup])

             (interpreter-start! interpreter)

             (casync/go
               (is (= (casync/<! c) :beep))
               (is (= (casync/<! c) :boop))
               (is (= (casync/<! c) :beep))
               (is (= (casync/<! c) :boop))
               (interpreter-send! interpreter :stop)
               (let [[re-ctx event {type :type}] (casync/<! c)
                     db (rf/get-coeffect re-ctx :db)]
                 (is (= db :isolated-value))
                 (is (= event [init-event]))
                 (is (= type "beeping")))
               (done))))))


(deftest activity-interceptors-test
  (testing "Activity interceptors handling"
    (async done

           (rf/reg-cofx
            ::test-coeffect-1
            (fn [cofx]
              (assoc cofx ::test-coeffect-1 1)))

           (let [c (casync/timeout 100) ;; If something goes wrong we shouldn't wait too long
                 interpreter (interpreter! (machine {:id       :simple-machine
                                                     :initial  :ready
                                                     :states  {:ready {:on {:stop :stop}
                                                                       :activities [:beeping]}
                                                               :stop {}}}

                                                    {:activities {:beeping (fx-activity
                                                                            [::test-coeffect-1]
                                                                            (fn [cofx]
                                                                              (let [*stop (atom nil)]
                                                                                (casync/go-loop [counter 1]
                                                                                  (let [pause (casync/timeout 10)]
                                                                                    (if-not @*stop
                                                                                      (do
                                                                                        (casync/>! c (if (= 1 (mod counter 2))
                                                                                                       :beep
                                                                                                       :boop))
                                                                                        (casync/<! pause)
                                                                                        (recur (inc counter)))
                                                                                      (casync/>! c cofx))))
                                                                                (fn []
                                                                                  (reset! *stop true)))))}}))]


             (interpreter-start! interpreter)

             (casync/go
               (is (= (casync/<! c) :beep))
               (is (= (casync/<! c) :boop))
               (is (= (casync/<! c) :beep))
               (is (= (casync/<! c) :boop))
               (interpreter-send! interpreter :stop)
               (let [cofx (casync/<! c)]
                 (is (= (::test-coeffect-1 cofx) 1))
               (done)))))))
