(ns maximgb.re-state.guards
  (:require [maximgb.re-state.utils :as utils]
            [maximgb.re-state.protocols :as protocols]
            [re-frame.core :as rf]))

(defn ev-guard
  "Returns a guard function which adopts re-frame context to the `handler` providing it with just event sent.

   `handler` is a function which recieves destructured event vector sent as it's arguments (& event+meta) -> boolean."
  [handler]
  (fn [re-ctx _ js-meta]
    (let [xs-event (utils/re-ctx->xs-event re-ctx)
          kv-meta (utils/js-meta->kv-argv (.-cond js-meta))]
      (apply handler (into xs-event kv-meta)))))


(defn db-guard
  "Returns a guard function which adopts re-frame context to the `handler` providing it with `db` co-effect.

  `handler` is a function similar to re-frame's reg-event-db handler but returns boolean: (db event-vector & meta) -> boolean."

  [handler]
  (fn [re-ctx _ js-meta]
    (let [db (rf/get-coeffect re-ctx :db)
          xs-event (utils/re-ctx->xs-event re-ctx)
          kv-meta (utils/js-meta->kv-argv (.-cond js-meta))]
      (apply handler (into [db xs-event] kv-meta)))))


(defn idb-guard
  "Returns a guard function which adopts re-frame context to the `handler` providing it with `db` co-effect.

  In contrast to `(db-guard)` this function isolates DB using interpreter path into isolated DB section.
  `handler` is a function similar to re-frame's reg-event-db handler but returns boolean: (db event-vector & meta) -> boolean."

  [handler]
  (fn [re-ctx _? js-meta]
    (let [db-guard-handler (db-guard handler)]
      (utils/call-with-re-ctx-db-isolated re-ctx
                                          (utils/re-ctx->interpreter-isolated-db-path re-ctx)
                                          db-guard-handler
                                          _?
                                          js-meta))))


(defn fx-guard
  "Returns a guard function which adopts re-frame context to the `handler` providing it with co-effects map.

  `handler` is a function similar to re-frame's reg-event-fx handler but returns boolean: (cofx event-vector & meta) -> boolean."

  [handler]
  (fn [re-ctx _ js-meta]
    (let [cofx (rf/get-coeffect re-ctx)
          xs-event (utils/re-ctx->xs-event re-ctx)
          kv-meta (utils/js-meta->kv-argv (.-cond js-meta))]
      (apply handler (into [cofx xs-event] kv-meta)))))


(defn ifx-guard
  "Returns a guard function which adopts re-frame context to the `handler` providing it with co-effects map.

  In contrast to `(fx-guard)` this function isolates DB using interpreter path into isolated DB section.
  `handler` is a function similar to re-frame's reg-event-fx handler but returns boolean: (cofx event-vector & meta) -> boolean."

  [handler]
  (fn [re-ctx _? js-meta]
    (let [fx-guard-handler (fx-guard handler)]
      (utils/call-with-re-ctx-db-isolated re-ctx
                                          (utils/re-ctx->interpreter-isolated-db-path re-ctx)
                                          fx-guard-handler
                                          _?
                                          js-meta))))


(defn ctx-guard
  "Returns a guard function which adopts re-frame context to the `handler` providing it with re-frame context.

  `handler` is a function similar to re-frame's reg-event-ctx handler but returns boolean: (re-ctx event & meta) -> boolean."

  [handler]
  (fn [re-ctx _ js-meta]
    (let [xs-event (utils/re-ctx->xs-event re-ctx)
          kv-meta (utils/js-meta->kv-argv (.-cond js-meta))]
      (apply handler (into [re-ctx xs-event] kv-meta)))))


(defn ictx-guard
  "Returns a guard function which adopts re-frame context to the `handler` providing it with re-frame context.

  In contrast to `(ctx-guard)` this function isolates DB using interpreter path into isolated DB section.
  `handler` is a function similar to re-frame's reg-event-ctx handler but returns boolean: (re-ctx event & meta) -> boolean."

  [handler]
  (fn [re-ctx _? js-meta]
    (let [ctx-guard-handler (ctx-guard handler)]
      (utils/call-with-re-ctx-db-isolated re-ctx
                                          (utils/re-ctx->interpreter-isolated-db-path re-ctx)
                                          ctx-guard-handler
                                          _?
                                          js-meta))))
