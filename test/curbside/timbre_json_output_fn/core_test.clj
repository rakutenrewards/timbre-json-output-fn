(ns curbside.timbre-json-output-fn.core-test
  (:require [clojure.test :refer :all]
            [jsonista.core :as json]
            [clojure.string :as string]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.core :as appenders]
            [curbside.timbre-json-output-fn.core :refer [json-output-fn]]))

(log/set-config! {:level :debug
                  :appenders {:println (appenders/println-appender {:stream :auto})}
                  :output-fn json-output-fn})

(def object-mapper (json/object-mapper {:decode-key-fn true}))

(defn parse-string [str]
  (json/read-value str object-mapper))

;; These tests below are sourced from timbre-json-appender test cases
(deftest only-message
  (is (= "Hello" (:msg (parse-string (with-out-str (log/info "Hello")))))))

(deftest only-args
  (let [log (parse-string (with-out-str (log/info :status 200 :duration 5)))]
    (is (= 200 (-> log :args :status)))
    (is (= 5 (-> log :args :duration)))))

(deftest message-and-args
  (let [log (parse-string (with-out-str (log/info "Task done" :duration 5)))]
    (is (= "Task done" (:msg log)))
    (is (= 5 (-> log :args :duration)))))

(deftest unserializable-value
  (testing "in a field"
    (is (= {} (-> (parse-string (with-out-str (log/info :a (Object.))))
                  :args
                  :a))))
  (testing "in ExceptionInfo"
    (is (= {} (-> (parse-string (with-out-str (log/info (ex-info "poks" {:a (Object.)}))))
                  :err
                  :data
                  :a)))))

(deftest exception
  (is (= "poks" (-> (parse-string (with-out-str (log/info (Exception. "poks") "Error")))
                    :err
                    :cause))))

(deftest format-string
  (is (= "Hello World!" (-> (parse-string (with-out-str (log/infof "Hello %s!" "World")))
                            :msg)))
  (let [log (parse-string (with-out-str (log/infof "%s %d%% ready" "Upload" 50 :role "admin")))]
    (is (= "Upload 50% ready"
           (:msg log)))
    (is (= {:role "admin"}
           (:args log)))))

;; Below is where our output format improves on timbre-json-appender...
(deftest format-string-odd-no-crash
  (testing "When logging with the format variant and an odd number of additional arguments, it handles gracefully"
    (let [{:keys [args msg]} (parse-string (with-out-str (log/infof "%s %d%% ready" "Upload" 50 :role)))]
      (is (= "Upload 50% ready" msg))
      (is (= ["role"] args)))))

(deftest exception-ex-info-data-serializable
  (testing "When there's an exception info throwned with data, the serializable data is available for inspection"
    (let [data {:a 10}
          {:keys [err]} (parse-string (with-out-str (log/info (ex-info "poks" data))))]
      (is (= data (:data err))))))

(deftest exception-stacktrace
  (testing "When there's an exception, stacktraces are also included in a text column"
    (let [stacktrace-lines (-> (parse-string (with-out-str (log/info (Exception. "poks") "Error")))
                               :stacktrace
                               (string/split #"\n"))]
      (is (not-empty stacktrace-lines))
      (is (= "java.lang.Exception: poks" (last stacktrace-lines))))))

(deftest just-a-map
  (testing "When logging only a map object, make it the args structure"
    (let [{:keys [args]} (parse-string (with-out-str (log/info {:status 200 :duration 5})))]
      (is (= 200 (-> args :status)))
      (is (= 5 (-> args :duration))))))

(deftest a-msg-and-map
  (let [{:keys [args msg]} (parse-string (with-out-str (log/info "a msg" {:status 200 :duration 5})))]
    (is (= "a msg" msg))
    (is (= {:duration 5
            :status 200} args))))

(deftest a-msg-and-two-map
  (let [{:keys [args msg]} (parse-string (with-out-str (log/info "a msg" {:status 200 :duration 5} {:status 300 :duration 10})))]
    (is (= [{:duration 5
             :status 200}
            {:duration 10
             :status 300}] args))
    (is (= "a msg" msg))))

(deftest a-msg-and-a-vector-of-map
  (let [{:keys [args msg]} (parse-string (with-out-str (log/info "a msg" [{:status 200 :duration 5}])))]
    (is (= [{:status 200 :duration 5}] (first args)))
    (is (= "a msg" msg))))
