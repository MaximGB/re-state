(ns maximgb.re-state.core
  (:require-macros [maximgb.re-state.core])
  (:require [maximgb.re-state.protocols :as protocols]
            [maximgb.re-state.impl.machine :as machine]
            [maximgb.re-state.impl.interpreter :as interpreter]
            [maximgb.re-state.impl.registry :as registry]
            [maximgb.re-state.guards :as guards]
            [maximgb.re-state.actions :as actions]
            [maximgb.re-state.utils :as utils]
            [maximgb.re-state.co-effects :as co-effects]
            [maximgb.re-state.effects :as effects]
            [maximgb.re-state.subscriptions :as subscriptions]))

(def machine->config protocols/machine->config)
(def machine->options protocols/machine->options)
(def machine->interceptors machine/machine->interceptors)
(def machine->xs-machine machine/machine->xs-machine)

(def machine<-options machine/machine<-options)

(def Machine machine/Machine)
(def machine machine/machine)

(def machine! machine/machine!)
(def machine-add-guard! machine/machine-add-guard!)
(def machine-add-action! machine/machine-add-action!)

(def init-event ::interpreter/xs-init)

(def interpreter->path protocols/interpreter->path)
(def interpreter->machine protocols/interpreter->machine)
(def interpreter->state protocols/interpreter->state)
(def interpreter->started? protocols/interpreter->started?)
(def interpreter-stop! protocols/interpreter-stop!)
(def interpreter-start! protocols/interpreter-start!)
(def interpreter-send! protocols/interpreter-send!)

(def interpreter! interpreter/interpreter!)

(def db-action actions/db-action)
(def fx-action actions/fx-action)
(def ctx-action actions/ctx-action)
(def idb-action actions/idb-action)
(def ifx-action actions/ifx-action)
(def ictx-action actions/ictx-action)

(def ev-guard guards/ev-guard)
(def db-guard guards/db-guard)
(def fx-guard guards/fx-guard)
(def ctx-guard guards/ctx-guard)
(def idb-guard guards/idb-guard)
(def ifx-guard guards/ifx-guard)
(def ictx-guard guards/ictx-guard)

(def reg-isub subscriptions/reg-isub)
(def isubscribe subscriptions/isubscribe)
(def isubscribe-state subscriptions/isubscribe-state)

(def register-interpreter! registry/register-interpreter!)
(def unregister-interpreter! registry/unregister-interpreter!)
(def has-interpreter? registry/has-interpreter?)
(def id->interpreter registry/id->interpreter)

(def cofx-instance ::cofx-instance)
(def cofx-spawn ::cofx-spawn)

(def fx-register ::fx-register)
(def fx-unregister ::fx-unregister)
(def fx-start ::fx-start)
(def fx-stop ::fx-stop)
(def fx-send ::fx-send)
