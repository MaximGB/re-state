(ns maximgb.re-state.impl.interpreter
  (:require [maximgb.re-state.protocols :as protocols]
            [maximgb.re-state.utils :as utils]
            [maximgb.re-state.impl.machine :as machine]
            [re-frame.core :as rf]
            [xstate :as xs]))


(defn- execute-transition-actions
  "Executes given `actions` in re-frame context `re-ctx`."
  [re-ctx actions]
  (areduce actions idx ret re-ctx
           (let [action (aget actions idx)
                 exec (or (aget action "exec") identity)
                 action-result (exec ret action)]
             (if (map? action-result)
               action-result
               ret))))


(defn- machine-actions->interceptors
  "Collects vector of unique action interceptors (#js [action]) -> [].

   If several actions require same interceptor the interceptor will be included only once."
  [machine actions]
  (let [interceptors (machine/machine->interceptors machine)]
    (last (areduce actions idx result [#{} []]
                   (-> (aget actions idx)
                       ((fn [^js/Object action]
                          (get interceptors (.-exec action))))
                       ((fn [action-interceptors]
                          (let [[result-interceptors-set result-interceptors-vec] result
                                action-interceptors-filtered (filterv (fn [interceptor]
                                                                        (not (result-interceptors-set interceptor)))
                                                                      action-interceptors)]
                            [(into result-interceptors-set action-interceptors-filtered)
                             (into result-interceptors-vec action-interceptors-filtered)]))))))))


;; Re-frame interceptor executing state transition actions
(def exec-interceptor
  (rf/->interceptor
   :id ::xs-actions-exec-interceptor
   :before (fn [re-ctx]
             (let [*interpreter (utils/re-ctx->*interpreter re-ctx)
                   xs-state (protocols/interpreter->state *interpreter)
                   actions (.-actions ^js/XState.State xs-state)]
               (execute-transition-actions re-ctx actions)))))


;; Re-frame interceptor storing interpreter state under interpreter path ::state keyword
(def store-state-interceptor
  (rf/->interceptor
   :id ::store-state-interceptor
   :before (fn [re-ctx]
             (let [interpreter (utils/re-ctx->*interpreter re-ctx)
                   interpreter-path (protocols/interpreter->path interpreter)
                   db (rf/get-coeffect re-ctx :db)
                   idb (get-in db interpreter-path)]
               (if (or (nil? idb) (and (associative? idb) (not (indexed? idb))))
                 ;; If isolated interpreter db part allows associtiation by keyword
                 (let [interpreter-state (protocols/interpreter->state interpreter)
                       new-db (assoc-in db
                                        (conj interpreter-path :maximgb.re-state.core/state)
                                        (.-value ^js/XState.State interpreter-state))]
                   (-> re-ctx
                       (rf/assoc-coeffect #_re-ctx :db new-db)
                       (rf/assoc-effect #_re-ctx :db new-db)))
                 ;; Else if we can't store state just returning re-ctx un-altered
                 re-ctx)))))


;; Re-frame event handler serving as the bridge between re-frame and XState
(rf/reg-event-ctx
 ::xs-transition-event
 (fn [re-ctx]
   (let [*interpreter (utils/re-ctx->*interpreter re-ctx)]
     (protocols/-interpreter-transition! *interpreter re-ctx))))


(defn- interpreter-
  [path machine]
  (let [*interpreter (volatile! {:state nil
                                 :started? false})]
    (reify
      IDeref

      (-deref [this] @*interpreter)

      protocols/InterpreterProto

      (interpreter->path [this]
        path)

      (interpreter->machine [this]
        machine)

      (interpreter->state [this]
        (:state @*interpreter))

      (interpreter->started? [this]
        (:started? @*interpreter))

      (-interpreter-start! [this init-payload]
        (let [started? (protocols/interpreter->started? this)]
          (if-not started?
            ;; Starting
            (do
              (vswap! *interpreter
                      assoc
                      :started? true)
              ;; Dispatching self-initialization event to transit to machine initial state
              (protocols/-interpreter-send! this (into [::xs-init] init-payload))))
          ;; Always return self
          this))

      (interpreter-stop! [this]
        (let [started? (protocols/interpreter->started? this)]
          (if started?
            (let [interpreter @*interpreter]
              ;; Updating self
              (vswap! *interpreter
                      assoc
                      :started? false)))
          this))

      (-interpreter-send! [this event]
        (rf/dispatch [::xs-transition-event this event])
        ;; Always return self
        this)

      protocols/-InterpreterProto

      (-interpreter-transition! [this re-ctx]
        (let [machine (protocols/interpreter->machine this)
              xs-machine (machine/machine->xs-machine machine)
              xs-event-type (utils/re-ctx->xs-event-type re-ctx)
              xs-current-state (protocols/interpreter->state this)
              xs-new-state ^js/XState.State (if xs-current-state
                                              (.transition ^js/XState.StateNode xs-machine
                                                           (.from ^js/XState.State xs/State xs-current-state re-ctx)
                                                           ;; Only type is needed for XState to make transition
                                                           ;; Event payload handlers will take from `re-ctx` afterwards
                                                           (clj->js xs-event-type))
                                              (.-initialState (^js/XState.StateNode .withContext
                                                                                     ^js/XState.StateNode xs-machine
                                                                                     re-ctx)))
              actions (.-actions xs-new-state)
              interceptors (machine-actions->interceptors machine actions)]
          (vswap! *interpreter
                  assoc
                  :state xs-new-state)
          (rf/enqueue re-ctx (conj interceptors store-state-interceptor exec-interceptor)))))))


(defn interpreter!
  "Creates XState based interpreter which uses re-frame facilities to send/receive and handle events"

  ([machine]
   (interpreter! (gensym ::instance) machine))

  ([path machine]
   (interpreter- (if (seqable? path)
                   path
                   [path])
                 machine)))
