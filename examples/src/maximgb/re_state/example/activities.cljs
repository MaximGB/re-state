(ns ^:figwheel-hooks maximgb.re-state.example.activities
  (:require [re-frame.core :as rf]
            [reagent.core :as reagent]
            [cljs.core.async :as async]
            [maximgb.re-state.core :as rs]))


(rs/def-machine blinking-machine
  {:initial :off
   :states {:off    {:entry       :initialize-db
                     :on         {:toggle    :on}}
            :on     {:on         {:toggle    :off
                                  :blink-on  {:actions :blink-on}
                                  :blink-off {:actions :blink-off}}
                     :activities [:blinking]}}})


(rs/def-activity-fx
  blinking-machine
  :blinking
  [[rs/re-state-service :instance []]]
  (fn [cofx _]
    (let [interpreter (get-in cofx [rs/re-state-service :instance])
          *stop (volatile! nil)]
      (async/go-loop [counter 0]
        (let [pause (async/timeout 1000)]
          (rs/interpreter-send! interpreter (if (= 0 (mod counter 2))
                                                :blink-on
                                                :blink-off))
          (async/<! pause)
          (if-not @*stop
            (recur (inc counter)))))
      (fn []
        (vreset! *stop true)))))

(rs/def-action-idb
  blinking-machine
  :initialize-db
  (fn [_]
    {:blink :off}))


(rs/def-action-idb
  blinking-machine
  :blink-on
  (fn [db]
    (assoc db :blink :on)))


(rs/def-action-idb
  blinking-machine
  :blink-off
  (fn [db]
    (assoc db :blink :off)))


(rs/reg-isub
 ::blink-state
 (fn [db]
   (:blink db)))


(defn blinker []
  (let [controller (rs/interpreter-start! (rs/interpreter! blinking-machine))
        blink-sub (rs/isubscribe [::blink-state controller])
        state-sub (rs/isubscribe-state controller)]
    (fn []
      [:div "Blinker!"
       [:button {:on-click #(rs/interpreter-send! controller :toggle)} "Toggle"]
       [:div "Machine state: " @state-sub]
       [:div "Blink state: " (if (= @blink-sub :on)
                               "On"
                               "Off")]])))


(defn ^:after-load -main []
  (reagent/render [blinker]
                  (.getElementById js/document "app")))


(.addEventListener js/window "load" -main)
