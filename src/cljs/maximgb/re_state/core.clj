(ns maximgb.re-state.core
  {:author "Maxim Bazhenov"})


(defmacro def-machine
  "Defines a var which is a reference to XState based state machine with given config."
  [var-name config]
  `(def ~var-name (machine! ~config)))


(defmacro def-action-db
  "Adds a DB-action with given `id` and handler function `action-fn` plus `interceptors` if needed to the machine defined by `var-name`."

  ([var-name id action-fn]
   `(machine-add-action! ~var-name ~id (db-action ~action-fn)))

  ([var-name id interceptors action-fn]
   `(machine-add-action! ~var-name ~id (db-action ~interceptors ~action-fn))))


(defmacro def-action-idb
  "Adds a DB-action with given `id` and handler function `action-fn` plus `interceptors` if needed to the machine defined by `var-name`."

  ([var-name id action-fn]
   `(machine-add-action! ~var-name ~id (idb-action ~action-fn)))

  ([var-name id interceptors action-fn]
   `(machine-add-action! ~var-name ~id (idb-action ~interceptors ~action-fn))))


(defmacro def-action-fx
  "Adds a FX-action with given `id` and handler function `action-fn` plus `interceptors` if needed to the machine defined by `var-name`."

  ([var-name id action-fn]
   `(machine-add-action! ~var-name ~id (fx-action ~action-fn)))

  ([var-name id interceptors action-fn]
   `(machine-add-action! ~var-name ~id (fx-action ~interceptors ~action-fn))))


(defmacro def-action-ifx
  "Adds a isolated FX-action with given `id` and handler function `action-fn` plus `interceptors` if needed to the machine defined by `var-name`."

  ([var-name id action-fn]
   `(machine-add-action! ~var-name ~id (ifx-action ~action-fn)))

  ([var-name id interceptors action-fn]
   `(machine-add-action! ~var-name ~id (ifx-action ~interceptors ~action-fn))))


(defmacro def-action-ctx
  "Adds a CTX-action with given `id` and handler function `action-fn` plus `interceptors` if needed to the machine defined by `var-name`."

  ([var-name id action-fn]
   `(machine-add-action! ~var-name ~id (ctx-action ~action-fn)))

  ([var-name id interceptors action-fn]
   `(machine-add-action! ~var-name ~id (ctx-action ~interceptors ~action-fn))))


(defmacro def-action-ictx
  "Adds a isolated CTX-action with given `id` and handler function `action-fn` plus `interceptors` if needed to the machine defined by `var-name`."

  ([var-name id action-fn]
   `(machine-add-action! ~var-name ~id (ictx-action ~action-fn)))

  ([var-name id interceptors action-fn]
   `(machine-add-action! ~var-name ~id (ictx-action ~interceptors ~action-fn))))


(defmacro def-guard-ev
  "Adds a EV-guard with given `id` and handler function `guard-fn` to the machine defined by `var-name`."

  ([var-name id guard-fn]
   `(machine-add-guard! ~var-name ~id (ev-guard ~guard-fn))))


(defmacro def-guard-db
  "Adds a DB-guard with given `id` and handler function `guard-fn` to the machine defined by `var-name`."

  [var-name id guard-fn]
  `(machine-add-guard! ~var-name ~id (db-guard ~guard-fn)))


(defmacro def-guard-idb
  "Adds a isolated DB-guard with given `id` and handler function `guard-fn` to the machine defined by `var-name`."

  [var-name id guard-fn]
  `(machine-add-guard! ~var-name ~id (idb-guard ~guard-fn)))


(defmacro def-guard-fx
  "Adds a FX-guard with given `id` and handler function `guard-fn` to the machine defined by `var-name`."

  [var-name id guard-fn]
   `(machine-add-guard! ~var-name ~id (fx-guard ~guard-fn)))

(defmacro def-guard-ifx
  "Adds a isolated FX-guard with given `id` and handler function `guard-fn` to the machine defined by `var-name`."

  [var-name id guard-fn]
  `(machine-add-guard! ~var-name ~id (ifx-guard ~guard-fn)))

(defmacro def-guard-ctx
  "Adds a CTX-guard with given `id` and handler function `guard-fn` to the machine defined by `var-name`."

  [var-name id guard-fn]
   `(machine-add-guard! ~var-name ~id (ctx-guard ~guard-fn)))

(defmacro def-guard-ictx
  "Adds a isolated CTX-guard with given `id` and handler function `guard-fn` to the machine defined by `var-name`."

  [var-name id guard-fn]
  `(machine-add-guard! ~var-name ~id (ictx-guard ~guard-fn)))

(defmacro def-ex-activity
  "Adds activity with given `id` and handler function `activity-fn` to the machine defined by `var-name`."
  [var-name id activity-fn]
  `(machine-add-activity! ~var-name ~id (ex-activity ~activity-fn)))
