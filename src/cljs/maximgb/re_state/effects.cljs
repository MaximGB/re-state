(ns maximgb.re-state.effects
  (:require [re-frame.core :as rf]
            [maximgb.re-state.protocols :as protocols]
            [maximgb.re-state.impl.registry :as registry]
            [maximgb.re-state.utils :as utils]))


(defn- normalize-interpreter
  [id]
  (if (satisfies? protocols/InterpreterProto id)
    id
    (registry/id->interpreter id)))


(defn- normalized-interpreter-start!
  [id payload]
  (protocols/-interpreter-start! (normalize-interpreter id)
                                (cond
                                  (nil? payload) []
                                  (utils/arglist? payload) payload
                                  :else [payload])))


(defn- normalized-interpreter-stop!
  [id]
  (protocols/interpreter-stop! (normalize-interpreter id)))


(defn- normalized-interpreter-send!
  [id event]
  (protocols/-interpreter-send! (normalize-interpreter id) event))


;; Registers interpreter instance in the registry
(rf/reg-fx
 :maximgb.re-state.core/fx-register
 (fn [id-and-interpreter]
   ;; Map is used to register multiple interpreters
   (if (map? id-and-interpreter)
     (doseq [[id interpreter] (seq id-and-interpreter)]
       (registry/register-interpreter! id interpreter))
     (let [[id interpreter] id-and-interpreter]
       (registry/register-interpreter! id interpreter)))))


;; Unregisters intrepreter instance in the registry
(rf/reg-fx
 :maximgb.re-state.core/fx-unregister
 (fn [ids]
   (if (seqable? ids)
     ;; Multipe ids unregistration
     (doseq [id ids]
       (registry/unregister-interpreter! id))
     ;; Single id unrgistration
     (registry/unregister-interpreter! ids))))


;; Starts interpreter
(rf/reg-fx
 :maximgb.re-state.core/fx-start
 (fn [id-and-payload]
   (cond
     ;; Map is used to start several interpreters
     ;; keys are ids or interpreter instances
     ;; values are payload
     (map? id-and-payload)
     (doseq [[id payload] (seq id-and-payload)]
       (normalized-interpreter-start! id payload))

     ;; Non-map seqables are used to pass single id + payload
     (seqable? id-and-payload)
     (let [[id & payload] id-and-payload]
       (normalized-interpreter-start! id payload))

     ;; single id
     :else
     (normalized-interpreter-start! id-and-payload nil))))


;; Stops interpreter
(rf/reg-fx
 :maximgb.re-state.core/fx-stop
 (fn [ids]
   (cond
     ;; Map might be used to stop several interpreters
     ;; keys are ids or interpreter instances
     ;; values are ignored
     (map? ids)
     (doseq [[id _] (seq ids)]
       (normalized-interpreter-stop! id))

     ;; Non-map seqable might be used to stop several interpeters
     (seqable? ids)
     (doseq [id ids]
       (normalized-interpreter-stop! id))

     ;; Single interpreter stop
     :else
     (normalized-interpreter-stop! ids))))


;; Sends an event to an interpreter
(rf/reg-fx
 :maximgb.re-state.core/fx-send
 (fn [id-and-event]
   (cond
     ;; Map might be used to send to several interpeters
     ;; keys are interpreter ids or interpreter instances
     ;; values are:
     ;;  - none-sequential - an event
     ;;  - sequential - [event & payload]
     (map? id-and-event)
     (doseq [[id event] (seq id-and-event)]
       (normalized-interpreter-send! id (if (seqable? event)
                                          event
                                          [event])))

     ;; Non-map seqable is an [id, event & payload]
     (seqable? id-and-event)
     (let [[id & event] id-and-event]
       (normalized-interpreter-send! id event))

     ;; Everything else is an error
     :else
     (throw (js/Error. "Don't know how to execute given send effect!")))))
