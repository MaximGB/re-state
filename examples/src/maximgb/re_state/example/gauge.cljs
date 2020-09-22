(ns ^:figwheel-hooks maximgb.re-state.example.gauge
  (:import  [goog.events BrowserEvent]
            [goog.dom DomHelper])
  (:require [goog.style :as gstyle]
            [re-frame.core :as rf]
            [reagent.core :as reagent]
            [maximgb.re-state.core :as rs]))


(def dom-helper (DomHelper.))


(rs/def-machine
  gauge-component-machine
  {:id      :gauge-component-machine
   :initial :initialized
   :states  {:initialized {:entry  :initialize-db
                           :on     {"" :ready}}

             :ready       {:on     {:thumb-pointer-down {:target  :steady
                                                         :actions [:save-drag-start-position :capture-pointer]}}}

             :steady      {:on     {:thumb-pointer-move {:cond    :has-drag-started?
                                                         :target  :dragging}

                                    :thumb-pointer-up   {:actions :release-pointer
                                                         :target  :ready}}}

             :dragging    {:on     {:thumb-pointer-move {:actions [:update-thumb-position :update-value-by-pos]}
                                    :thumb-pointer-up   {:actions :release-pointer
                                                         :target  :ready}}}}})


(def initial-db {:min 0
                 :max 100
                 :val 0
                 :pos 0
                 :drag-threshold 3
                 :drag-start-pos nil})


(defn- calculate-percentage-value
  [min max pos]
  (if-not (= min max)
    (* 100 (/ (- pos min) (- max min)))
    min))


(rs/def-action-idb
  gauge-component-machine
  :initialize-db
  (fn [db [_ & {:keys [min max val drag-threshold]}]]
    (let [min-norm (or min (:min initial-db))
          max-norm (or max (:max initial-db))
          val-norm (or val (:val initial-db))
          dt-norm  (or drag-threshold (:drag-threshold initial-db))]
      (-> db
          (merge initial-db)
          (assoc :min min-norm
                 :max max-norm
                 :val val-norm
                 :pos (calculate-percentage-value min-norm max-norm val-norm))))))


(rs/def-action-idb
  gauge-component-machine
  :save-drag-start-position
  (fn [db [_ goog-browser-event]]
    (assoc db
           :drag-start-pos [(.-clientX goog-browser-event)
                            (.-clientY goog-browser-event)])))


(rs/def-guard-idb
  gauge-component-machine
  :has-drag-started?
  (fn [db [_ goog-browser-event]]
    (let [drag-threshold (:drag-threshold db)
          [drag-start-x drag-start-y] (:drag-start-pos db)
          current-x (.-clientX goog-browser-event)
          current-y (.-clientY goog-browser-event)]
      (<= drag-threshold
          (.sqrt js/Math (+ (.pow js/Math (- current-x drag-start-x) 2)
                            (.pow js/Math (- current-y drag-start-y) 2)))))))


(rs/def-action-idb
  gauge-component-machine
  :update-thumb-position
  (fn [db [_ goog-browser-event] & {:keys [update-value?] :or {update-value false}}]
    (let [event-client-x (.-clientX goog-browser-event)
          thumb (.-target goog-browser-event)
          container (.getAncestorByClass dom-helper thumb "gauge-scale-container")
          container-client-pos (gstyle/getClientPosition container)
          container-size (gstyle/getSize container)
          container-width (.-width container-size)
          container-client-xmin (.-x container-client-pos)
          container-client-xmax (+ container-client-xmin container-width)
          new-thumb-x (->> event-client-x
                           (max container-client-xmin)
                           (min container-client-xmax))
          new-thumb-pos (calculate-percentage-value container-client-xmin container-client-xmax new-thumb-x)]
      (assoc db
             :pos new-thumb-pos))))


(rs/def-action-idb
  gauge-component-machine
  :update-value-by-pos
  (fn [db]
    (let [min (:min db)
          max (:max db)
          pos (:pos db)]
      (assoc db
             :val (+ min (/ (* pos (- max min)) 100))))))


(rs/def-action-ifx
  gauge-component-machine
  :capture-pointer
  (fn [cofx [_ goog-browser-event]]
    {:capture-pointer [(.-target    goog-browser-event)
                       (.-pointerId goog-browser-event)]}))


(rs/def-action-ifx
  gauge-component-machine
  :release-pointer
  (fn [cofx [_ goog-browser-event]]
    {:release-pointer [(.-target goog-browser-event)
                       (.-pointerId goog-browser-event)]}))


(rf/reg-fx
 :capture-pointer
 (fn [[el pointerId]]
   (.setPointerCapture el pointerId)))


(rf/reg-fx
 :release-pointer
 (fn [[el pointerId]]
   (.releasePointerCapture el pointerId)))


(rs/reg-isub
 :thumb-pos
 (fn [db]
   (:pos db)))


(rs/reg-isub
 :value
 (fn [db]
   (:val db)))


(defn gauge-component [& {:keys [min max val drag-threshold]}]
  (let [controller (rs/interpreter-sync-start! (rs/interpreter! gauge-component-machine)
                                               :min min
                                               :max max
                                               :val val
                                               :drag-threshold drag-threshold)
        thumb-pos-sub (rs/isubscribe [:thumb-pos controller])
        value-sub     (rs/isubscribe [:value controller])]

    (fn []
      [:div.gauge-container
       [:div.gauge-scale-container.position-relative.m-2
        [:div.divider.border
         {:style {:position :absolute
                  :top "50%"
                  :margin 0
                  :width "100%"
                  :height "3px"}}]
        [:button.btn.btn-sm.btn-info.rounded-circle

         {:style {:transform "translateX(-50%)"
                  :left (str @thumb-pos-sub "%")
                  :position :relative
                  :cursor :pointer}

          :on-pointer-down #(rs/interpreter-send! controller
                                                  :thumb-pointer-down
                                                  (do (.persist %)
                                                      %))

          :on-pointer-move #(rs/interpreter-send! controller
                                                  :thumb-pointer-move
                                                  (do (.persist %)
                                                      %))

          :on-pointer-up #(rs/interpreter-send!   controller
                                                  :thumb-pointer-up
                                                  (do (.persist %)
                                                      %))}
         "⊙"]]
       [:div.gauge-value
        {:style {:text-align :center}}
        (str (.round js/Math @value-sub) "%")]])))


(defn ^:after-load -main []
  (reagent/render [:div.m-2
                   "Drag & drop the red thumb to change the value."
                   [gauge-component]]
                  (.getElementById js/document "app")))


(.addEventListener js/window "load" -main)
