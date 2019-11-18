(ns maximgb.re-state.services.re-state
  (:require [maximgb.re-service.core :as rs]
            [maximgb.re-state.protocols :as protocols]
            [maximgb.re-state.utils :as utils]
            [maximgb.re-state.impl.interpreter :as iimpl]))


;; Interpreter registry
(def *iregistry (volatile! {}))


(defn has-interpreter?
  "Checks if an interpreter with the given `id` is registered."
  [id]
  (not (nil? (@*iregistry id))))


(defn id->interpreter
  "Gets interpreter with the given `id` from the registry."
  [id]
  (@*iregistry id))


(defn with-interpreter-normalized
  "Calls a `fn` with the `args` for the given by instance or by `id` intrepreter."
  [i fn & [args]]
  (let [inorm (if (satisfies? protocols/InterpreterProto i)
                i
                (id->interpreter i))]
    (apply fn inorm args)))


;; ------------------------------------------------

(rs/def-re-service :maximgb.re-state.core/re-state)


;; Returns instance of the interpreter registered or the one executing event handler
(rs/def-re-service-command
  :maximgb.re-state.core/re-state
  :instance
  [cofx & [id]]
  (if (nil? id)
    (utils/cofx->interpreter cofx)
    (@*iregistry id)))


;; Spawn a new interpreter with the given machine
(rs/def-re-service-command
  :maximgb.re-state.core/re-state
  :spawn!
  [cofx & [machine]]
  (if machine
    (iimpl/interpreter! machine)
    (-> cofx
        (utils/cofx->interpreter)
        (protocols/interpreter->machine)
        (iimpl/interpreter!))))


;; Register interpreter in the registry
(rs/def-re-service-command
  :maximgb.re-state.core/re-state
  :register!
  [id interpreter]
  (vswap! *iregistry
          assoc
          id
          interpreter))


;; Unregisters interpreter from the registry
(rs/def-re-service-command
  :maximgb.re-state.core/re-state
  :unregister!
  [id]
  (vswap! *iregistry
          dissoc
          id))


;; Starts interpreter passing given args to (interpreter-start!) function
(rs/def-re-service-command
  :maximgb.re-state.core/re-state
  :start!
  [id & args]
  (with-interpreter-normalized id protocols/interpreter-start! args))


;; Stops intrepreter
(rs/def-re-service-command
  :maximgb.re-state.core/re-state
  :stop!
  [id]
  (with-interpreter-normalized id protocols/interpreter-stop!))


;; Sends intrepreter the given event, event is usually [event-type & args]
(rs/def-re-service-command
  :maximgb.re-state.core/re-state
  :send!
  [id & event]
  (with-interpreter-normalized id protocols/interpreter-send! event))
