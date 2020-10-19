(ns maximgb.re-state.subscriptions
  (:require [re-frame.core :as rf]
            [maximgb.re-state.protocols :as protocols]
            [maximgb.re-state.utils :as utils]))


(rf/reg-sub
 :maximgb.re-state.core/sub-interpreter-root
 (fn [db [_ interpreter]]
   (let [path (protocols/interpreter->path interpreter)]
     (get-in db path))))


(defn- isubscribe-root
  "Creates a reaction to changes in `interpreter` path isolated part of the app db."
  [interpreter]
  (rf/subscribe [:maximgb.re-state.core/sub-interpreter-root interpreter]))


(rf/reg-sub
 :maximgb.re-state.core/sub-interpreter-db
 (fn [[_ interpreter]]
   (isubscribe-root interpreter))
 (fn [db]
   (:db db)))


(defn isubscribe
  "Creates a reaction to changes in `interpreter` path isolated part of the app db.

   Can take just `interpreter` as the parameter and will subscribe to the interpreter isolated db changes,
   or can take a subscription query vector passing it to rf/subscribe unaltered in this case."
  [interpreter]
  (rf/subscribe (if (vector? interpreter)
                  interpreter
                  [:maximgb.re-state.core/sub-interpreter-db interpreter])))


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


(rf/reg-sub
 :maximgb.re-state.core/sub-interpreter-state
 (fn [[_ interpreter]]
   (isubscribe-root interpreter))
 (fn [idb [_ interpreter keywordize?]]
   (let [state (get idb (protocols/interpreter->id interpreter))]
     (if keywordize?
       (utils/keywordize-state state)
       (js->clj state)))))


(defn isubscribe-state
  "Creates a reaction to changes in `interpreter` state.

  Warning: state names will be returned as keywords, if this is not desired use :keywordize? false option.
  Warning: if state names are given as namespaced keywords, like ::state, then this function will return them un-namespaced,
           this is due to the fact that state names are converted to strings first, to be passed to JS world, and then
           re-created from strings to keywords back, the namespace information is lost during the conversion."
  [interpreter & {:keys [keywordize?] :or {keywordize? true}}]
  (rf/subscribe [:maximgb.re-state.core/sub-interpreter-state interpreter keywordize?]))
