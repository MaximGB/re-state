(ns ^:figwheel-hooks maximgb.re-state.example.actions
  (:require [re-frame.core :as rf]
            [reagent.core :as reagent]
            [maximgb.re-state.core :as rs]))


(rs/def-machine traffic-light-machine
  {:initial :off
   :states {:off    {:on {:toggle    {:target  :green
                                      :actions [:turn-off-lights :turn-on-green]}}}

            :green  {:on {:to-yellow {:target  :yellow
                                      :actions [:turn-off-lights :turn-on-yellow]}

                          :toggle    {:target  :off
                                      :actions :turn-off-lights}}}

            :yellow {:on {:to-red    {:target  :red
                                      :actions [:turn-off-lights :turn-on-red]}

                          :toggle    {:target  :off
                                      :actions :turn-off-lights}}}

            :red    {:on {:to-green  {:target  :green
                                      :actions [:turn-off-lights :turn-on-green]}

                          :toggle    {:target :off
                                      :actions :turn-off-lights}}}}})

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
      [:<>
       [:div
        [(if @green-sub
           :button.btn.btn-success.btn-lg.rounded-circle
           :button.btn.btn-outline-dark.btn-lg.rounded-circle.disabled)
         {:on-click #(rs/interpreter-send! controller :to-yellow)}
         "G"]
        [(if @yellow-sub
           :button.btn.btn-warning.btn-lg.rounded-circle
           :button.btn.btn-outline-dark.btn-lg.rounded-circle.disabled)
         {:on-click #(rs/interpreter-send! controller :to-red)}
         "Y"]
        [(if @red-sub
           :button.btn.btn-danger.btn-lg.rounded-circle
           :button.btn.btn-outline-dark.btn-lg.rounded-circle.disabled)
         {:on-click #(rs/interpreter-send! controller :to-green)}
         "R"]]
       [:div.btn-group.mt-2
        [(if (= @state :off)
           :button.btn.btn-success
           :button.btn.btn-outline-dark.disabled)
         {:on-click #(rs/interpreter-send! controller :toggle)}
         "On"]
        [:div.or]
        [(if (not= @state :off )
           :button.btn.btn-danger
           :button.btn.btn-outline-dark.disabled)
         {:on-click #(rs/interpreter-send! controller :toggle)}
         "Off"]]])))


(defn ^:after-load -main []
  (reagent/render [:div.d-flex.flex-column
                   [:div.text-center.m-2 "Turn the machine on and then click on the active light to transit to the next mode."]
                   [:div.flex-grow-1.d-flex.flex-column.align-items-center
                    [traffic-light]]]
                  (.getElementById js/document "app")))


(.addEventListener js/window "load" -main)
