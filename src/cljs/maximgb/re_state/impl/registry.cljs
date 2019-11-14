(ns maximgb.re-state.impl.registry
  (:require [maximgb.re-state.protocols :as protocols]))

;; Interpreter registry, used by effects/co-effects to spawn interpreters and send events between them
(def *iregistry (volatile! {}))


(defn register-interpreter!
  "Registers `intrepreter` with the given `id` in the registry."
  [id interpreter]
  {:pre [(satisfies? protocols/InterpreterProto interpreter)]}
  (vswap! *iregistry
          assoc
          id
          interpreter))


(defn unregister-interpreter!
  "Unregisters interpreter with the given `id` from the registry."
  [id]
  (vswap! *iregistry
          dissoc
          id))


(defn has-interpreter?
  "Checks if an interpreter with the given `id` is registered."
  [id]
  (not (nil? (@*iregistry id))))


(defn id->interpreter
  "Gets interpreter with the given `id` from the registry."
  [id]
  {:post [(not (nil? %))]}
  (@*iregistry id))
