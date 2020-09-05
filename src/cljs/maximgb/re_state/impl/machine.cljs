(ns maximgb.re-state.impl.machine
  (:require [maximgb.re-state.protocols :as protocols]
            [maximgb.re-state.utils :as utils]
            [xstate :as xs]))


(def machine->interceptors (memoize protocols/-machine->interceptors))
(def machine->xs-machine (memoize protocols/-machine->xs-machine))


(defn- make-machine-interceptors
  "Extracts interceptors map from machine config and options.

   The map contains JavaScript callable function as keys and their metadata as values. Machine config and options are scanned for
   handler function with meta data attached and this map is constructed. Machine will be constructed with config and options containing
   handlers cleared of metadata. This is needed because ClojureScript encodes function with metadata into `MetaFn` objects which vanilla
   JavaScript can't call. Thus to preserve metadata with map is used."
  [config options]
  (merge (utils/machine-config->actions-interceptors config :bare? false)
         (utils/machine-options->actions-interceptors options :bare? false)
         (utils/machine-options->activities-interceptors options :bare? false)))


(defn- make-machine-xs-machine
  "Creates js/XState machine instance from config and options."
  [config options]
  (xs/Machine (utils/prepare-machine-config config)
              (utils/prepare-machine-options options)))


(defrecord Machine [config options]
  protocols/MachineProto

  (machine->config [this]
    config)

  (machine->options [this]
    options)

  (-machine->interceptors [this]
    (let [config (protocols/machine->config this)
          options (protocols/machine->options this)]
      (make-machine-interceptors config options)))

  (-machine->xs-machine [this]
    (let [config (protocols/machine->config this)
          options (protocols/machine->options this)]
      (make-machine-xs-machine config options))))


(defn machine
  "Creates a XState based machine record with given definition and optional options"

  ([config]
   (machine config {}))

  ([config options]
   (map->Machine {:config config
                  :options options})))


(defn machine!
  "Creates reference to a XState based machine which can be updated in-place.

   This kind of machine is needed for machine definition DSL."
  [config]

  (let [*machine (volatile! (machine config))]
    (reify
      IDeref

      (-deref [this] @*machine)

      protocols/MachineProto

      (machine->config [this]
        (protocols/machine->config @*machine))

      (machine->options [this]
        (protocols/machine->options @*machine))

      (-machine->interceptors [this]
        (protocols/-machine->interceptors @*machine))

      (-machine->xs-machine [this]
        (protocols/-machine->xs-machine @*machine))

      protocols/-MachineProto

      (-machine<-options [this apply-fn args]
        (vswap! *machine
                assoc
                :options
                (apply apply-fn (:options @*machine) args))
        this))))


(defn machine<-options
  "Sets machine options as if (apply apply-fn current-options args)."
  [*machine apply-fn & args]
  (protocols/-machine<-options *machine apply-fn args))


(defn machine-add-action!
  "Adds an action with `id` to machine options."
  [*machine id action-fn]
  (machine<-options *machine
                    (fn [options]
                      (assoc-in options [:actions id] action-fn))))


(defn machine-add-guard!
  "Adds a guard with `id` to machine options."
  [*machine id guard-fn]
  (machine<-options *machine
                    (fn [options]
                      (assoc-in options [:guards id] guard-fn))))


(defn machine-add-activity!
  "Adds an activity with `id` to machine options"
  [*machine id activity-fn]
  (machine<-options *machine
                    (fn [options]
                      (assoc-in options [:activities id] activity-fn))))
