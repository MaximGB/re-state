(ns maximgb.re-state.services.spawn
  (:require [maximgb.re-service.core :as rs]))


(rs/def-re-service :maximgb.re-state.core/spawn)


(def *activities (volatile! {}))


(rs/def-re-service-command
  :maximgb.re-state.core/spawn
  :start-activity
  [id start-fn & args]
  (vswap! *activities assoc id (apply start-fn args)))


(rs/def-re-service-command
  :maximgb.re-state.core/spawn
  :stop-activity
  [id]
  (if-let [stop-fn (@*activities id)]
    (stop-fn))
  (vswap! *activities dissoc id))
