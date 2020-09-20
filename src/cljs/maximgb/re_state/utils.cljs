(ns maximgb.re-state.utils
  (:require [re-frame.core :as rf]
            [com.rpl.specter :as specter]
            [maximgb.re-state.protocols :as protocols]))

(defn arglist?
  "Opinionatedly checks if a sequence can be used as arglist"
  [s]
  (and s (seqable? s) (not (map? s))))


(defn cofx->interpreter
  "Gets interpreter instance from re-frame `:event` co-effect."
  [cofx]
  (let [[_ interpreter] (:event cofx)]
    interpreter))


(defn re-ctx->*interpreter
  "Gets interpreter instance from re-frame context's `:event` co-effect."
  [re-ctx]
  (cofx->interpreter (rf/get-coeffect re-ctx)))


(defn re-ctx->xs-event
  "Gets `xs-event` structure from re-frame context's `:event` co-effect."
  [re-ctx]
  (let [[_ _ xs-event] (rf/get-coeffect re-ctx :event)]
    xs-event))


(defn re-ctx->xs-event-type
  "Gets `xs-event` type from re-frame context's `:event` co-effect."
  [re-ctx]
  (first (re-ctx->xs-event re-ctx)))


(defn interpreter->isolated-db-path
  "Gets isolated interpreter database path in the re-frame database."
  [interpreter]
  (-> interpreter
      (protocols/interpreter->path)
      (conj :db)))


(defn re-ctx->interpreter-isolated-db-path
  "Gets isolated interpreter path in the re-frame database for the interpreter sent event in re-frame context."
  [re-ctx]
  (-> re-ctx
      (re-ctx->*interpreter)
      (interpreter->isolated-db-path)))


(defn interpreter->isolated-state-path
  "Get isolated interpreter state path in the re-frame database."
  [interpreter]
  (-> interpreter
      (protocols/interpreter->path)
      (conj :state)))


(defn js-meta->kv-argv
  "Transforms JS meta object into a vector of key value pairs."
  [js-meta]
  (->> (js->clj js-meta :keywordize-keys true)
      (seq)
      (flatten)
      (into [])))


(defn meta-fn->js-fn
  "Extracts JavaScript callable function from ClojureScript only callable MetaFn object"
  [meta-fn]
  (.-afn meta-fn))


;; TODO: simplify the path
(def MACHINE-CONFIG-ACTIONS (specter/recursive-path []
                                                    p
                                                    [:states
                                                     specter/MAP-VALS
                                                     (specter/multi-path [:on specter/MAP-VALS (specter/must :actions) (specter/if-path seqable? specter/ALL specter/STAY) #(instance? MetaFn %)]
                                                                         [(specter/must :entry) (specter/if-path seqable? specter/ALL specter/STAY) #(instance? MetaFn %)]
                                                                         [(specter/must :exit) (specter/if-path seqable? specter/ALL specter/STAY) #(instance? MetaFn %)]
                                                                         p)]))


;; TODO: simplify the path
(def MACHINE-OPTIONS-ACTIONS [(specter/must :actions)
                              specter/MAP-VALS
                              (specter/if-path seqable? specter/ALL specter/STAY)
                              #(instance? MetaFn %)])

;; TODO: simplify the path
(def MACHINE-OPTIONS-ACTIVITIES [(specter/must :activities)
                                 specter/MAP-VALS
                                 (specter/if-path seqable? specter/ALL specter/STAY)
                                 #(instance? MetaFn %)])


(defn prepare-machine-config
  "Scans `config` of a XState machine and adopts it for JavaScript usage.

   There might be handlers with metadata, objects of MetaFn type which are not callable
   by JavaScript host, thus they should be converted back to normal `js/Function` type.
   Also the function does (clj->js) transformation."
  [config]
  (->> config
       (specter/transform MACHINE-CONFIG-ACTIONS meta-fn->js-fn)
       (clj->js)))


(defn prepare-machine-options
  "Scans `options` of a XState machine and adopts it for JavaScript usage.

   There might be handlers with metadata, objects of MetaFn type which are not callable
   by JavaScript host, thus they should be converted back to normal `js/Function` type.
   Also the function does (clj->js) transformation."
  [options]
    (->> options
        (specter/transform MACHINE-OPTIONS-ACTIONS meta-fn->js-fn)
        (specter/transform MACHINE-OPTIONS-ACTIVITIES meta-fn->js-fn)
        (clj->js)))


(defn meta-handlers->interceptors-map
  "Transforms sequence of actions with interceptors metadata into a map where keys are metaless normall JS functions and values are list of interceptors."
  [actions & {:keys [bare?] :or {bare? true}}]
  (reduce (fn [m action-fn]
            (let [interceptors (:maximgb.re-state.core/xs-interceptors (meta action-fn))]
              (assoc m
                     (meta-fn->js-fn action-fn)
                     (if-not bare?
                       (map (fn [interceptor]
                              (cond
                                ;; Single keyword/symbol/string/number is considered as interceptor id
                                (or (keyword? interceptor) (symbol? interceptor) (string? interceptor) (number? interceptor))
                                (rf/inject-cofx interceptor)
                                ;; Sequence or vector is considered as co-effect id & rest params
                                (or (seq? interceptor) (vector? interceptor))
                                (rf/inject-cofx (first interceptor) (rest interceptor))
                                ;; Everything else is considered to be an interceptor re-frame can handle on itself
                                :else
                                interceptor))
                            interceptors)
                       interceptors))))
          {}
          actions))


(defn machine-config->actions-interceptors
  "Extracts interceptors metadata from actions given in machine configuration.

   Returns a map with original action functions as keys and handlers' metadata as value,
   such that it can be easily looked up during runtime."
  [config & {:keys [bare?] :or {bare? true}}]
  (meta-handlers->interceptors-map (specter/select MACHINE-CONFIG-ACTIONS config)
                                   :bare? bare?))


(defn machine-options->actions-interceptors
  "Extracts interceptors metadata from actions given in machine options.

   Returns a map with original action functions as keys and handlers' metadata as value,
   such that it can be easily looked up during runtime."
  [options & {:keys [bare?] :or {bare? true}}]
  (meta-handlers->interceptors-map (specter/select MACHINE-OPTIONS-ACTIONS options)
                                   :bare? bare?))


(defn machine-options->activities-interceptors
  "Extracts interceptors metadata from activities given in machine options.

   Returns a map with original activities functions as keys and handlers' metadata as value,
   such that it can be easily looked up during runtime."
  [options & {:keys [bare?] :or {bare? true}}]
  (meta-handlers->interceptors-map (specter/select MACHINE-OPTIONS-ACTIVITIES options)
                                   :bare? bare?))


(defn call-with-re-ctx-db-isolated
  "Calls `inner-fn` having isolated re-frame's context :db co-effect/effect using provided `path`, returns inner-fn call result.

   `inner-fn` is called as (inner-fn new-re-ctx & inner-args)."
  [re-ctx path inner-fn & inner-args]
  (let [;; Getting un-isolated db
        udb (rf/get-coeffect re-ctx :db)
        ;; Getting isolated db part
        idb (get-in udb path)
        ;; Changing :db coeffect/effect to hold isolated db
        ictx (-> re-ctx
                 (rf/assoc-coeffect #_re-ctx :db idb)
                 (rf/assoc-effect #_re-ctx :db idb))]
    ;; Calling handler, recieving new context with isolated :db coeffect and possible db changes in :db effect
    (apply inner-fn ictx inner-args))) ;; <! - HANDLER CALL


(defn with-re-ctx-db-isolated
  "Calls `inner-fn` having isolated re-frame's context `:db` co-effect/effect using provided `path`, returns updated context with `:db` unisolated.

   `inner-fn` is called as (inner-fn new-re-ctx & inner-args)."
  [re-ctx path inner-fn & inner-args]
  (let [;; Getting un-isolated db
        udb (rf/get-coeffect re-ctx :db)
        ;; Getting isolated db part
        idb (get-in udb path)
        ;; Changing :db coeffect/effect to hold isolated db
        ictx (-> re-ctx
                 (rf/assoc-coeffect :db idb)
                 (rf/assoc-effect :db idb))
        ;; Calling handler, recieving new context with isolated :db coeffect and possible db changes in :db effect
        new-ictx (apply inner-fn ictx inner-args) ;; <! - HANDLER CALL
        ;; Getting new isolated db part
        new-idb (rf/get-effect new-ictx :db)]
    (if (not= new-idb idb)
      ;; If handler changed :db effect then removing isolation and propagating changes to both co-effects and effects
      (let [new-udb (assoc-in udb path new-idb)
            new-ctx (-> new-ictx
                        (rf/assoc-effect #_new-ictx :db new-udb)
                        ;; Restoring back to co-effect because we are in environment with multiple event handlers
                        (rf/assoc-coeffect #_new-ictx :db new-udb))]
        new-ctx)
      ;; Else if handler haven't manipulated isolated db then just removing isolation in both co-effects and effects
      (let [new-ctx (-> new-ictx
                        (rf/assoc-effect #_new-ictx :db udb)
                        ;; Restoring back to co-effect because we are in environment with multiple event handlers
                        (rf/assoc-coeffect #_new-ictx :db udb))]
        new-ctx))))
