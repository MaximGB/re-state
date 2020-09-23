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
                                  :blink     {:actions :blink}}
                     :activities [:blinking]}}})


(rs/def-activity-fx
  blinking-machine
  :blinking
  [[rs/re-state-service :instance []]]
  (fn [cofx _]
    (let [interpreter (get-in cofx [rs/re-state-service :instance])
          *stop (volatile! nil)]
      (async/go-loop [counter 0]
        (let [pause (async/timeout 300)]
          (rs/interpreter-send! interpreter :blink counter)
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


(def blink-opts [:green :yellow :red])


(rs/def-action-idb
  blinking-machine
  :blink
  (fn [db [_ counter]]
    (assoc db :blink (blink-opts (mod counter 3)))))


(rs/reg-isub
 ::blink-state
 (fn [db]
   (:blink db)))


(defn blinker []
  (let [controller (rs/interpreter-start! (rs/interpreter! blinking-machine))
        blink-sub (rs/isubscribe [::blink-state controller])
        state-sub (rs/isubscribe-state controller)]
    (fn []
      (let [blink-state @blink-sub
            state       @state-sub]
        [:<>
         [:div
          [(if (= blink-state :green)
             :button.btn.btn-success.btn-lg.rounded-circle
             :button.btn.btn-outline-dark.btn-lg.rounded-circle.disabled)
           "G"]
          [(if (= blink-state :yellow)
             :button.btn.btn-warning.btn-lg.rounded-circle
             :button.btn.btn-outline-dark.btn-lg.rounded-circle.disabled)
           "Y"]
          [(if (= blink-state :red)
             :button.btn.btn-danger.btn-lg.rounded-circle
             :button.btn.btn-outline-dark.btn-lg.rounded-circle.disabled)
           "R"]]
         [:div.btn-group.mt-2
          [(if (= state :off)
             :button.btn.btn-success
             :button.btn.btn-outline-dark.disabled)
           {:on-click #(rs/interpreter-send! controller :toggle)}
           "On"]
          [(if (= state :on)
             :button.btn.btn-danger
             :button.btn.btn-outline-dark.disabled)
           {:on-click #(rs/interpreter-send! controller :toggle)}
           "Off"]]]))))



(defn ^:after-load -main []

  (reagent/render [:div.d-flex.flex-column
                   [:div.text-center.m-2 "Turn the machine on and and watch activity running."]
                   [:div.flex-grow-1.d-flex.flex-column.align-items-center
                    [blinker]]]
                  (.getElementById js/document "app")))


(.addEventListener js/window "load" -main)
