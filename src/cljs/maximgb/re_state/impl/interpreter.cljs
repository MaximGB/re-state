(ns maximgb.re-state.impl.interpreter
  (:require [maximgb.re-state.protocols :as protocols]
            [maximgb.re-state.utils :as utils]
            [maximgb.re-state.impl.machine :as machine]
            [re-frame.core :as rf]
            [xstate :as xs]))


(defmulti execute-action
  "Executes X-State action.

   Each action is given as js-object with `exec` or `type` property. The method analyzes `type` first and then fallbacks to `exec`
   if `type` is unknown or not given and `exec` is defined. If none is given method returns first argument which is re-frame context."
  (fn [_re-ctx action]
    (aget "type" action)))


(defmethod execute-action
  :default
  [re-ctx action]
  (let [exec (or (aget action "exec") identity)
        result (exec re-ctx action)]
    (if (map? result)
      result
      re-ctx)))


(defn- execute-transition-actions
  "Executes given `actions` in re-frame context `re-ctx`.

   Each action recieves `re-ctx` as the only argument and should return ether something `map?` like, which is considered
   to be updated `re-ctx`, which, in it's turn will be passed to the next action. Or action return nothing, which means
   that it hasn't added any modifications to the `re-ctx` and the next action will recieve unaltered context.

   Actions here are not those function a re-state user defines, this actions are wrappers around user's actions. This wrappers
   adopt `re-ctx` to the kind of argument user's action expects. This might be: db, cofx map, re-ctx."
  [re-ctx actions]
  (areduce actions idx ret re-ctx
           (let [action (aget actions idx)]
             (execute-action ret action))))


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
(def actions-exec-interceptor
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
                                        (conj interpreter-path :state)
                                        (.-value ^js/XState.State interpreter-state))]
                   (-> re-ctx
                       (rf/assoc-coeffect :db new-db)
                       (rf/assoc-effect :db new-db)))
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

      (-interpreter-start! [this init-payload sync?]
        (let [started? (protocols/interpreter->started? this)]
          (when-not started?
            ;; Starting
            (vswap! *interpreter
                    assoc
                    :started? true)
            ;; Dispatching self-initialization event to transit to machine initial state
            (protocols/-interpreter-send! this (into [::xs-init] init-payload) sync?))
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

      (-interpreter-send! [this event sync?]
        ((if sync? rf/dispatch-sync rf/dispatch) [::xs-transition-event this event])
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
          (prn "Transiting " xs-event-type)
          (vswap! *interpreter
                  assoc
                  :state xs-new-state)
          (rf/enqueue re-ctx (conj interceptors
                                   store-state-interceptor
                                   actions-exec-interceptor)))))))


(defn interpreter!
  "Creates XState based interpreter which uses re-frame facilities to send/receive and handle events"

  ([machine]
   (interpreter! nil machine))

  ([path machine]
   (let [valid-path (or path (gensym ::instance))]
     (interpreter- (if (seqable? valid-path)
                     valid-path
                     [valid-path])
                   machine))))
