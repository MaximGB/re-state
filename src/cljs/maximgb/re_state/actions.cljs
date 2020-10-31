(ns maximgb.re-state.actions
  (:require [maximgb.re-state.utils :as utils]
            [re-frame.core :as rf]
            [maximgb.re-state.protocols :as protocols]))


(defn- db-action->re-ctx-handler
  "Adopts `handler` function to be called with `re-ctx` re-frame context.

   Adopting function extracts all `handler` required params from `re-ctx` and passes them to the `handler`,
   then associates `handler` return value to `re-ctx` `:db` effect and co-effect"
  [handler]
  (fn [re-ctx js-meta]
   (let [db (rf/get-coeffect re-ctx :db)
         xs-event (utils/re-ctx->xs-event re-ctx)
         kv-meta (utils/js-meta->kv-argv js-meta)
         new-db (or (apply handler (into [db xs-event] kv-meta)) db)]
     (-> re-ctx
         ;; Assoc into both since there might be other action handlers which
         ;; read from `db` coeffect
         (rf/assoc-effect :db new-db)
         (rf/assoc-coeffect :db new-db)))))


(defn db-action
  "Returns an intercepting function which adopts re-frame context to the `handler` and injects handler result back into re-frame context.

  `handler` is a function similar to re-frame's reg-event-db handler (db event-vector & meta) -> db.

   The function also contains required interceptors in it's metadata."

  ([handler]
   (db-action [] handler))

  ([interceptors handler]
   (with-meta
     (db-action->re-ctx-handler handler)
     {:maximgb.re-state.core/xs-interceptors interceptors})))


(defn idb-action
  "Returns an intercepting function which adopts re-frame context to the `handler` and injects handler result back into re-frame context.

  In contrast to `(db-action)` this function isolates DB using interpreter path into isolated DB section.
  `handler` is a function similar to re-frame's reg-event-db handler (db event-vector & meta) -> db.

  The function also contains required interceptors in it's metadata."

  ([handler]
   (idb-action [] handler))

  ([interceptors handler]
   (let [db-handler (db-action->re-ctx-handler handler)]
     (with-meta
       (fn [re-ctx js-meta]
         (utils/with-re-ctx-db-isolated
           re-ctx
           (utils/re-ctx->interpreter-isolated-db-path re-ctx)
           db-handler
           js-meta))
       {:maximgb.re-state.core/xs-interceptors interceptors}))))


(defn- fx-action->re-ctx-handler
  "Adopts `handler` function to be called with `re-ctx` re-frame context.

   Adopting function extracts all `handler` required params from `re-ctx` and passes them to the `handler`.
   `handler` return value is effects map which will be associated back to `re-ctx` `:effects` map.
   `:db` effect is handled differently from other effects, it's associtated with `re-ctx` `:db` co-effect as well, since there might
   be other handlers which also might read `:db` co-effect. If it's not be updated then next handler will use outdated `:db` value."
  [handler]
  (fn [re-ctx js-meta]
    (let [cofx (rf/get-coeffect re-ctx)
          xs-event (utils/re-ctx->xs-event re-ctx)
          kv-meta (utils/js-meta->kv-argv js-meta)
          new-effects (apply handler (into [cofx xs-event] kv-meta))]
      (-> re-ctx
          ;; TODO: maybe extract into util/merge-fx
          ((fn [re-ctx]
             (if (map? new-effects)
               (reduce (fn [re-ctx [effect-key effect-val]]
                         (let [old-effect (rf/get-effect re-ctx effect-key)]
                           (cond
                             (= effect-key :db) ;; :db effect doesn't stack since it's handled in a special way later in the function
                             (rf/assoc-effect re-ctx effect-key effect-val)

                             (:maximgb.re-state.core/stacked-effect (meta old-effect))
                             (rf/assoc-effect re-ctx effect-key (conj old-effect effect-val))

                             old-effect
                             (rf/assoc-effect re-ctx effect-key ^:maximgb.re-state.core/stacked-effect [old-effect effect-val])

                             :else
                             (rf/assoc-effect re-ctx effect-key effect-val))))
                       re-ctx
                       new-effects)
               re-ctx)))
          ;; Special :db handling since there might be other action handlers
          ;; reading :db from :coeffects
          ((fn [re-ctx]
             (rf/assoc-coeffect re-ctx
                                :db
                                (rf/get-effect re-ctx :db))))))))


(defn fx-action
  "Returns an intercepting function which adopts re-frame context to the `handler` and injects handler result back into re-frame context.

  `handler` is function similar to re-frame's reg-event-fx handler (cofx-map event-vector & meta) -> fx-map.

  The function also contains required interceptors in it's metadata."

  ([handler]
   (fx-action [] handler))

  ([interceptors handler]
   (with-meta
     (fx-action->re-ctx-handler handler)
     {:maximgb.re-state.core/xs-interceptors interceptors})))


(defn ifx-action
  "Returns an intercepting function which adopts re-frame context to the `handler` and injects handler result back into re-frame context.

  In contrast to `(fx-action)` this function isolates DB using interpreter path into isolated DB section.
  `handler` is function similar to re-frame's reg-event-fx handler (cofx-map event-vector & meta) -> fx-map.

  The function also contains required interceptors in it's metadata."
  ([handler]
   (ifx-action [] handler))

  ([interceptors handler]
   (let [fx-handler (fx-action->re-ctx-handler handler)]
     (with-meta
       (fn [re-ctx js-meta]
         (utils/with-re-ctx-db-isolated
           re-ctx
           (utils/re-ctx->interpreter-isolated-db-path re-ctx)
           fx-handler
           js-meta))
       {:maximgb.re-state.core/xs-interceptors interceptors}))))


(defn- ctx-action->re-ctx-handler
  "Adopts `handler` function to be called with `re-ctx` re-frame context.

   Adopting function extracts all `handler` required params from `re-ctx` and passes them to the `handler`.
   `handler` return value is considered to be new `re-ctx`."
  [handler]
  (fn [re-ctx js-meta]
    (let [xs-event (utils/re-ctx->xs-event re-ctx)
          kv-meta (utils/js-meta->kv-argv js-meta)]
      (apply handler (into [re-ctx xs-event] kv-meta)))))


(defn ctx-action
  "Returns an intercepting function which adopts re-frame context to the `handler`.

   `handler` is function similar to re-frame's reg-event-ctx handler (re-ctx event-vector & meta) -> re-ctx.

  The function also contains required interceptors in it's metadata."

  ([handler]
   (ctx-action [] handler))

  ([interceptors handler]
   (with-meta
     (ctx-action->re-ctx-handler handler)
     {:maximgb.re-state.core/xs-interceptors interceptors})))


(defn ictx-action
  "Returns an intercepting function which adopts re-frame context to the `handler`.

  In contrast to `(ctx-action)` this function isolates DB using interpreter path into isolated DB section.
   `handler` is function similar to re-frame's reg-event-ctx handler (re-ctx event-vector & meta) -> re-ctx.

  The function also contains required interceptors in it's metadata."

  ([handler]
   (ictx-action [] handler))

  ([interceptors handler]
   (let [ctx-handler (ctx-action->re-ctx-handler handler)]
     (with-meta
       (fn [re-ctx js-meta]
         (utils/with-re-ctx-db-isolated
           re-ctx
           (utils/re-ctx->interpreter-isolated-db-path re-ctx)
           ctx-handler
           js-meta))
       {:maximgb.re-state.core/xs-interceptors interceptors}))))
