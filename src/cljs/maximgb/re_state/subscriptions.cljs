(ns maximgb.re-state.subscriptions
  (:require [re-frame.core :as rf]
            [maximgb.re-state.protocols :as protocols]))


(rf/reg-sub
 :maximgb.re-state.core/sub-interpreter-db
 (fn [db [_ interpreter]]
   (let [path (protocols/interpreter->path interpreter)]
     (get-in db path))))


(defn isubscribe
  "Creates a reaction to changes in `interpreter` path isolated part of the app db."
  [interpreter]
  (rf/subscribe [:maximgb.re-state.core/sub-interpreter-db interpreter]))


(defn reg-isub
  "Creates subscription to isolated by interpreter path part of the application database.

   The subscribe call should pass interpreter as the first item in the subscription query:

       (rf/subscribe [::my-sub interpreter other query parts])

   where ::my-sub should be registered with (reg-isub ::my-sub computation-fn) call."
  [id computation-fn]
  (rf/reg-sub
   id
   (fn [[_ interpreter]]
     (isubscribe interpreter))
   computation-fn))


(reg-isub
 :maximgb.re-state.core/sub-interpreter-state
 (fn [idb [_ _ keywordize?]]
   (let [state (:state idb)]
     (cond
       (and (string? state) keywordize?)
       (keyword state)

       (string? state)
       state

       keywordize?
       (js->clj state :keywordize-keys true)

       :else
       (js->clj state)))))


(defn isubscribe-state
  "Creates a reaction to changes in `interpreter` state.

  Warning: state names will be returned as keywords, if this is not desired use :keywordize? false option.
  Warning: if state names are given as namespaced keywords, like ::state, then this function will return them un-namespaced,
           this is due to the fact that state names are converted to strings first, to be passed to JS world, and then
           re-created from strings to keywords back, the namespace information is lost during the conversion."
  [interpreter & {:keys [keywordize?] :or {keywordize? true}}]
  (rf/subscribe [:maximgb.re-state.core/sub-interpreter-state interpreter keywordize?]))
