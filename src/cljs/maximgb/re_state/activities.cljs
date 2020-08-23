(ns maximgb.re-state.activities)

;; - co-effects to obtain system information
;;   - db (i)
;;   - cofx (i)
;;   - ctx (i)
;; - meta
(defn ex-activity
  [handler]
  (fn [ctx meta]
    (handler)))
