(ns maximgb.re-state.protocols)

(defprotocol MachineProto
  "XState based machine protocol.

   The protocol is used to obtain machine config/options unaltered by clj->js/js->clj transformations."
  (machine->config [this]
    "Returns machine config as a Clojure map")
  (machine->options [this]
    "Returns machine options as a Clojure map")
  (-machine->interceptors [this]
    "Returns map of machine actions interceptors")
  (-machine->xs-machine [this]
    "Returns XState machine instance"))


(defprotocol -MachineProto
  "XState based referential machine protocol.

   The protocol is needed to implement machine definition DSL."
  (-machine<-options [this apply-fn args]
    "Changes machine options by applying `apply-fn` to current machine options and optional args"))


(defprotocol InterpreterProto
  "XState based interpreter protocol which uses re-frame facilities to send/recieve and handle events"
  (interpreter->path [this]
    "Returns interpreter data path in app-db.")
  (interpreter->machine ^Machine [this]
    "Returns currently interpreting machine.")
  (interpreter->state ^Object  [this]
    "Returns currently active state id.")
  (interpreter->started? ^boolean  [this]
    "Checks if interpreter has been started.")
  (-interpreter-start! ^InterpreterProto [this event-data sync?]
    "Starts machine interpretation. Registers re-frame event handlers to recieve events of the machine.")
  (interpreter-stop! ^InterpreterProto [this]
    "Stops machine interpretation. Un-registers re-frame event handlers registered at (start) call.")
  (-interpreter-send! ^InterpreterProto [this event sync?]
    "Sends an event to the machine via re-frame facilities. `event` is [event & payload]."))


(defprotocol -InterpreterProto
  "Module private interpreter protocol, users should not implement or call it's methods."

  (-interpreter-transition! [this re-ctx]
    "Does the state chart transition.

     Returns re-frame context."))


(defn interpreter-start!
  "Starts interpreter optionally passing addition payload for `::xs-init` event."
  [interpreter & init-payload]
  (-interpreter-start! interpreter init-payload false))


(defn interpreter-sync-start!
  "Synchroniously starts interpreter optionally passing addition payload for `::xs-init` event."
  [interpreter & init-payload]
  (-interpreter-start! interpreter init-payload true))


(defn interpreter-send!
  "Sends an event to XState machine via re-frame facilities and initiates re-frame event processing using XState machine actions."
  [interpreter event & payload]
  (-interpreter-send! interpreter
                      (into [event] payload)
                      false))


(defn interpreter-sync-send!
  "Synchroniously sends an event to XState machine via re-frame facilities and initiates re-frame event processing using XState machine actions."
  [interpreter event & payload]
  (-interpreter-send! interpreter
                      (into [event] payload)
                      true))
