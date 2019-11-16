(ns ^:figwheel-hooks maximgb.re-state.example.basic
  (:require [re-frame.core :as rf]
            [reagent.core :as reagent]
            [maximgb.re-state.core :as rs]))


(rs/def-machine basic-machine {:id      :basic-machine
                               :initial :one
                               :states {:one   {:on {:click :two}}
                                        :two   {:on {:click :three}}
                                        :three {:on {:click :one}}}} )


(defn state-cycler []
  (let [controller (rs/interpreter-start! (rs/interpreter! basic-machine))
        state-sub (rs/isubscribe-state controller)]
    (fn []
      [:div
       "Current state is: "
       [:div {:style {:display :inline-block
                      :width "5em"}}
        @state-sub]
       [:button
        {:on-click #(rs/interpreter-send! controller :click)}
        "Next state"]])))


(defn ^:after-load -main []
  (reagent/render [:div
                   [:div "State cycler component, press \"Next state\" button to cycle states."]
                   [state-cycler]]
                  (.getElementById js/document "app")))


(.addEventListener js/window "load" -main)
