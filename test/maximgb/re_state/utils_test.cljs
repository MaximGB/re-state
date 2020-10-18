(ns maximgb.re-state.utils-test
  (:require [cljs.test :refer [deftest is testing async use-fixtures]]
            [goog.object :as gobject]
            [maximgb.re-state.core :refer [db-action ev-activity]]
            [maximgb.re-state.utils :refer [prepare-machine-config
                                            prepare-machine-options
                                            meta-handlers->interceptors-map
                                            machine-config->actions-interceptors
                                            machine-options->actions-interceptors
                                            machine-options->activities-interceptors
                                            js-meta->kv-argv
                                            call-with-re-ctx-db-isolated
                                            with-re-ctx-db-isolated
                                            keywordize-state]]))


(def test-machine-config {:id :test-2
                          :initial :ready
                          :states {:ready {:entry [:ready-entry]
                                           :exit (db-action
                                                  [:test-coeffect-1 :test-coeffect-2]
                                                  (fn [db]))
                                           :on {:toggle {:target :running
                                                         :actions [:ready-to-running (db-action
                                                                                      [:test-coeffect-3]
                                                                                      (fn [db]))]}}}
                                   :running {:initial :fast
                                             :states {:fast {:entry :in-running-fast
                                                             :initial :very
                                                             :states {:very {:exit :out-running-fast
                                                                             :on {:stop {:target :ready
                                                                                         :actions [(db-action
                                                                                                    [:test-coeffect-4]
                                                                                                      (fn [db]))]}}}}}}}}})

(def test-machine-options {:actions {:action-1 (db-action
                                                [:test-coeffect-1]
                                                (fn [db]))
                                     :action-2 (db-action
                                                [:test-coeffect-1 :test-coeffect-2]
                                                (fn [db]))}
                           :activities {:activity-1 (ev-activity
                                                     [:test-coeffect-3]
                                                     (fn [ev]))}})


(deftest meta-handlers-to-interceptors-map-test
  (testing "Interceptors extractions from meta actions sequence"
    (let [a-fn (fn [])
          b-c-fn (fn [])
          actions [(with-meta a-fn {:maximgb.re-state.core/xs-interceptors [:a]})
                   (with-meta b-c-fn {:maximgb.re-state.core/xs-interceptors [:b :c]})]
          interceptors (meta-handlers->interceptors-map actions)]
      (is (= (get interceptors a-fn) [:a]) "A-fn interceptors are extracted correctly")
      (is (= (get interceptors b-c-fn) [:b :c]) "B-C-fn interceptors are extracted correctly"))))


(deftest machine-config-interceptors-extraction-test
  (testing "Actions interceptors extraction from machine config"
    (let [meta (machine-config->actions-interceptors test-machine-config)
          interceptors (into #{} (flatten (vals meta)))
          fns (keys meta)]
      (doseq [f fns]
        (is (instance? js/Function f) "All meta keys are normal JS functions"))
      (is (= interceptors #{:test-coeffect-1 :test-coeffect-2 :test-coeffect-3 :test-coeffect-4}) "Config actions interceptors collected"))))


(deftest machine-options-actions-interceptors-extraction-test
  (testing "Actions interceptors extranction from machine options"
    (let [meta (machine-options->actions-interceptors test-machine-options)
          interceptors (into #{} (flatten (vals meta)))
          fns (keys meta)]
      (doseq [f fns]
        (is (instance? js/Function f) "All meta keys are normal JS functions"))
      (is (= interceptors #{:test-coeffect-1 :test-coeffect-2}) "Options actions interceptors collected"))))


(deftest machine-options-activities-interceptors-extraction-test
  (testing "Activities interceptors extranction from machine options"
    (let [meta (machine-options->activities-interceptors test-machine-options)
          interceptors (into #{} (flatten (vals meta)))
          fns (keys meta)]
      (doseq [f fns]
        (is (instance? js/Function f) "All meta keys are normal JS functions"))
      (is (= interceptors #{:test-coeffect-3}) "Options activities interceptors collected"))))


(deftest prepare-machine-config-test
  (testing "Meta actions defined in machine config should be transformed to normal js/Function type and their meta data removed"
    (let [entry-fn #()
          exit-fn #()
          on-fn-1 #()
          on-fn-2 #()
          config {:id :test-machine
                  :initial :ready
                  :states {:ready {:entry [(with-meta entry-fn {:a :a})]
                                   :on {:toggle {:target :running
                                                 :actions [(with-meta on-fn-1 {:b :b})
                                                           (with-meta on-fn-2 {:c :c})]}}}
                           :running {:exit (with-meta exit-fn {:d :d})}}}
          config-prepared (prepare-machine-config config)
          paths {[:states :ready :entry 0] entry-fn
                 [:states :ready :on :toggle :actions 0] on-fn-1
                 [:states :ready :on :toggle :actions 1] on-fn-2
                 [:states :running :exit] exit-fn}]
      (doseq [[path fn] paths]
        (is (identical? (gobject/getValueByKeys config-prepared (clj->js path)) fn) "Metaless correct function found")))))


(deftest prepare-machine-options-test
  (testing "Meta actions defined in machine options should be transformed to normal js/Function type and their meta data removed"
    (let [fn1 #()
          fn2 #()
          options {:guards {}
                   :actions {:fn1 (with-meta fn1 {:a :a})
                             :fn2 (with-meta fn2 {:b :b})}}
          options-prepared (prepare-machine-options options)
          paths {[:actions :fn1] fn1
                 [:actions :fn2] fn2}]
      (doseq [[path fn] paths]
        (is (identical? (gobject/getValueByKeys options-prepared (clj->js path)) fn) "Metaless correct function found")))))


(deftest js-meta->kv-argv-test
  (testing "Conversion of Javascript metadata object into vector of key/value pairs."
    (let [meta #js {:a 1
                    :b 2}
          kv-argv (js-meta->kv-argv meta)]
      (is (= kv-argv [:a 1 :b 2]) "Coversion correct"))))


(deftest call-with-re-ctx-db-isolated-test
  (testing "Calling with re-frame context :db isolated"
    (let [path [:a :b :c]
          db {:a {:b {:c ::my-val}}}
          ctx {:coeffects {:db db}}
          inner-fn (fn [re-ctx v]
                     (= (get-in re-ctx [:coeffects :db]) v))]
      (is (call-with-re-ctx-db-isolated ctx path inner-fn ::my-val) "Call returns true correctly"))))


(deftest with-re-ctx-db-isolated-test
  (testing "Re-frame context :db isolation utility"
    (let [path-1 [:a :b :c]
          path-2 [:a :b :d]
          db {:z 1}
          ctx {:coeffects {:db db}}
          inner-fn (fn [re-ctx v]
                      (assoc-in re-ctx [:effects :db :v] v))
          new-ctx (-> ctx
                      (with-re-ctx-db-isolated path-1 inner-fn 1)
                      (with-re-ctx-db-isolated path-2 inner-fn 2))]
      (is (= (get-in new-ctx [:effects :db])
             {:a {:b {:c {:v 1}
                      :d {:v 2}}}
              :z 1})))))


(deftest keywordize-state-test
  (testing "XState keywordize convertion utility"
    (is (= (keywordize-state "a") :a))
    (is (= (keywordize-state :a) :a))
    (is (= (keywordize-state 16) 16))
    (is (= (keywordize-state #js {:a 10}) {:a 10}))
    (is (= (keywordize-state #js {"a" #js {"b" "c"
                                           "d" "e"}})
           {:a {:b :c
                :d :e}}))))
