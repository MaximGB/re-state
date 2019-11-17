(ns ^:figwheel-hooks maximgb.re-state.example.entryexit
  (:require [re-frame.core :as rf]
            [reagent.core :as reagent]
            [maximgb.re-state.core :as rs]))


(rs/def-machine traffic-light-machine
  {:initial :off

   :states {:off    {:on    {:toggle :green}}

            :green  {:entry :turn-on-green
                     :exit  :turn-off-lights
                     :on    {:to-yellow :yellow
                             :toggle    :off}}

            :yellow {:entry :turn-on-yellow
                     :exit  :turn-off-lights
                     :on    {:to-red  :red
                             :toggle  :off}}

            :red    {:entry :turn-on-red
                     :exit  :turn-off-lights
                     :on    {:to-green :green
                             :toggle   :off}}}})

(rs/def-action-db
  traffic-light-machine
  :turn-off-lights
  (fn [db]
    (assoc db
           :green  false
           :yellow false
           :red    false)))


(rs/def-action-db
  traffic-light-machine
  :turn-on-green
  (fn [db]
    (assoc db
           :green true)))


(rs/def-action-db
  traffic-light-machine
  :turn-on-yellow
  (fn [db]
    (assoc db
           :yellow true)))


(rs/def-action-db
  traffic-light-machine
  :turn-on-red
  (fn [db]
    (assoc db
           :red true)))


(rf/reg-sub
 :green
 (fn [db]
   (:green db)))


(rf/reg-sub
 :yellow
 (fn [db]
   (:yellow db)))


(rf/reg-sub
 :red
 (fn [db]
   (:red db)))


(defn traffic-light []
  (let [controller (rs/interpreter-start! (rs/interpreter! traffic-light-machine))
        state (rs/isubscribe-state controller)
        green-sub (rf/subscribe [:green])
        yellow-sub (rf/subscribe [:yellow])
        red-sub (rf/subscribe [:red])]
    (fn []
      [:div
       [(if @green-sub
          :button.ui.massive.circular.green.icon.button
          :button.ui.massive.circular.disabled.icon.button)
        {:on-click #(rs/interpreter-send! controller :to-yellow)}]
       [(if @yellow-sub
          :button.ui.massive.circular.yellow.icon.button
          :button.ui.massive.circular.disabled.icon.button)
        {:on-click #(rs/interpreter-send! controller :to-red)}]
       [(if @red-sub
          :button.ui.massive.circular.red.icon.button
          :button.ui.massive.circular.disabled.icon.button)
        {:on-click #(rs/interpreter-send! controller :to-green)}]
       [:div {:style {:margin-top "0.5em"}}
        [:div.ui.buttons
         [(if (= @state :off)
            :button.ui.button.positive
            :button.ui.button.disabled)
          {:on-click #(rs/interpreter-send! controller :toggle)}
          "On"]
         [:div.or]
         [(if (not= @state :off )
            :button.ui.button.negative
            :button.ui.button.disabled)
          {:on-click #(rs/interpreter-send! controller :toggle)}
          "Off"]]]])))


(defn ^:after-load -main []
  (reagent/render [:div
                   [:div "Click on the active light to transit to the next mode."]
                   [traffic-light]]
                  (.getElementById js/document "app")))


(.addEventListener js/window "load" -main)
