(ns maximgb.re-state.activities
  (:require [maximgb.re-state.utils :as utils]
            [re-frame.core :as rf]
            [maximgb.re-state.protocols :as protocols]
            [maximgb.re-state.protocols :as protocols]))


(defn- ev-activity->re-ctx-handler
  "Adopts activity `handler` function to be called with `re-ctx` re-frame context.

   Adopting function extracts all `handler` required params from `re-ctx` and passes them to the `handler`."
  [handler]
  (fn [re-ctx js-meta]
    (let [xs-event (utils/re-ctx->xs-event re-ctx)
          kv-meta (utils/js-meta->kv-argv js-meta)]
      (apply handler (into xs-event kv-meta)))))


(defn ev-activity
  "Returns a function which adopts re-frame context to the activity `handler` providing it with just event sent.

   `handler` is a function which recieves destructured event vector sent as it's arguments (& event+meta) -> boolean."
  ([handler]
   (ev-activity [] handler))

  ([interceptors handler]
   (with-meta
     (ev-activity->re-ctx-handler handler)
     {:maximgb.re-state.core/xs-interceptors interceptors})))


(defn- db-activity->re-ctx-handler
  "Adopts activity `handler` function to be called with `re-ctx` re-frame context.

   Adopting function extracts all `handler` required params from `re-ctx` and passes them to the `handler`"
  [handler]
  (fn [re-ctx js-meta]
    (let [db (rf/get-coeffect re-ctx :db)
          xs-event (utils/re-ctx->xs-event re-ctx)
          kv-meta (utils/js-meta->kv-argv js-meta)]
      (apply handler (into [db xs-event] kv-meta)))))


(defn db-activity
  "Returns a function which adopts re-frame context to the activity `handler`.

  `handler` is a function similar to re-frame's reg-event-db handler (db event-vector & meta) -> stop-fn.

   The function also contains required interceptors in it's metadata."
  ([handler]
   (db-activity [] handler))

  ([interceptors handler]
   (with-meta
     (db-activity->re-ctx-handler handler)
     {:maximgb.re-state.core/xs-interceptors interceptors})))

(defn idb-activity
  "Returns a function which adopts re-frame context to the activity `handler`.

  In contrast to `(db-activity)` this function isolates DB using interpreter path into isolated DB section.

  `handler` is a function similar to re-frame's reg-event-db handler (db event-vector & meta) -> stop-fn.

  The function also contains required interceptors in it's metadata."
  ([handler]
   (idb-activity [] handler))

  ([interceptors handler]
   (let [db-handler (db-activity->re-ctx-handler handler)]
     (with-meta
       (fn [re-ctx js-meta]
         (utils/call-with-re-ctx-db-isolated
          re-ctx
          (utils/re-ctx->interpreter-isolated-db-path re-ctx)
          db-handler
          js-meta))
      {:maximgb.re-state.core/xs-interceptors interceptors}))))


(defn- fx-activity->re-ctx-handler
  "Adopts activity `handler` function to be called with `re-ctx` re-frame context.

   Adopting function extracts all `handler` required params from `re-ctx` and passes them to the `handler`"
  [handler]
  (fn [re-ctx js-meta]
    (let [cofx (rf/get-coeffect re-ctx)
          xs-event (utils/re-ctx->xs-event re-ctx)
          kv-meta (utils/js-meta->kv-argv js-meta)]
      (apply handler (into [cofx xs-event] kv-meta)))))


(defn fx-activity
  "Returns a function which adopts re-frame context to the activity `handler`.

  `handler` is function similar to re-frame's reg-event-fx handler (cofx-map event-vector & meta) -> stop-fn.

  The function also contains required interceptors in it's metadata."
  ([handler]
   (fx-activity [] handler))

  ([interceptors handler]
   (with-meta
     (fx-activity->re-ctx-handler handler)
     {:maximgb.re-state.core/xs-interceptors interceptors})))


(defn ifx-activity
  "Returns a function which adopts re-frame context to the activity `handler`.

  In contrast to `(fx-activity)` this function isolates DB using interpreter path into isolated DB section.

  `handler` is function similar to re-frame's reg-event-fx handler (cofx-map event-vector & meta) -> stop-fn.

  The function also contains required interceptors in it's metadata."
  ([handler]
   (ifx-activity [] handler))

  ([interceptors handler]
   (let [fx-handler (fx-activity->re-ctx-handler handler)]
     (with-meta
       (fn [re-ctx js-meta]
         (utils/call-with-re-ctx-db-isolated
          re-ctx
          (utils/re-ctx->interpreter-isolated-db-path re-ctx)
          fx-handler
          js-meta))
       {:maximgb.re-state.core/xs-interceptors interceptors}))))


(defn- ctx-activity->re-ctx-handler
  "Adopts activity `handler` function to be called with `re-ctx` re-frame context.

   Adopting function extracts all `handler` required params from `re-ctx` and passes them to the `handler`"
  [handler]
  (fn [re-ctx js-meta]
    (let [xs-event (utils/re-ctx->xs-event re-ctx)
          kv-meta (utils/js-meta->kv-argv js-meta)]
      (apply handler (into [re-ctx xs-event] kv-meta)))))


(defn ctx-activity
  "Returns a function which adopts re-frame context to the activity `handler`.

  `handler` is function similar to re-frame's reg-event-ctx handler (re-ctx event-vector & meta) -> stop-fn.

  The function also contains required interceptors in it's metadata."
  ([handler]
   (ctx-activity [] handler))

  ([interceptors handler]
   (with-meta
     (ctx-activity->re-ctx-handler handler)
     {:maximgb.re-state.core/xs-interceptors interceptors})))


(defn ictx-activity
  "Returns a function which adopts re-frame context to the activity `handler`.

  In contrast to `(ctx-activity)` this function isolates DB using interpreter path into isolated DB section.

  `handler` is function similar to re-frame's reg-event-ctx handler (re-ctx event-vector & meta) -> stop-fn.

  The function also contains required interceptors in it's metadata."
  ([handler]
   (ictx-activity [] handler))

  ([interceptors handler]
   (let [ctx-handler (ctx-activity->re-ctx-handler handler)]
     (with-meta
       (fn [re-ctx js-meta]
         (utils/call-with-re-ctx-db-isolated
          re-ctx
          (utils/re-ctx->interpreter-isolated-db-path re-ctx)
          ctx-handler
          js-meta))
       {:maximgb.re-state.core/xs-interceptors interceptors}))))
