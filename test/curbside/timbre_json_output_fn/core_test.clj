(ns curbside.timbre-json-output-fn.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [jsonista.core :as json]
            [clojure.string :as string]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.core :as appenders]
            [curbside.timbre-json-output-fn.core :refer [make-json-output-fn]]))

(log/set-config! {:level :debug
                  :appenders {:println (appenders/println-appender {:stream :auto})}
                  :output-fn (make-json-output-fn)})

(def object-mapper (json/object-mapper {:decode-key-fn true}))

(defn parse-string [str]
  (json/read-value str object-mapper))

;; These tests below are sourced from timbre-json-appender test cases
(deftest only-message
  (is (= "Hello" (:message (parse-string (with-out-str (log/info "Hello")))))))

(deftest only-args
  (let [log (parse-string (with-out-str (log/info :status 200 :duration 5)))]
    (is (= 200 (-> log :args :status)))
    (is (= 5 (-> log :args :duration)))))

(deftest message-and-args
  (let [log (parse-string (with-out-str (log/info "Task done" :duration 5)))]
    (is (= "Task done" (:message log)))
    (is (= 5 (-> log :args :duration)))))

(deftest unserializable-value
  (testing "in a field"
    (is (= {} (-> (parse-string (with-out-str (log/info :a (Object.))))
                  :args
                  :a))))
  (testing "in ExceptionInfo"
    (is (= {} (-> (parse-string (with-out-str (log/info (ex-info "poks" {:a (Object.)}))))
                  :error
                  :map
                  :data
                  :a)))))

(deftest exception
  (is (= "poks" (-> (parse-string (with-out-str (log/info (Exception. "poks") "Error")))
                    :error
                    :map
                    :cause))))

(deftest format-string
  (is (= "Hello World!" (-> (parse-string (with-out-str (log/infof "Hello %s!" "World")))
                            :message)))
  (let [log (parse-string (with-out-str (log/infof "%s %d%% ready" "Upload" 50 :role "admin")))]
    (is (= "Upload 50% ready"
           (:message log)))
    (is (= {:role "admin"}
           (:args log)))))

;; Below is where our output format improves on timbre-json-appender...
(deftest logger-information
  (testing "Logger information is logged into logger.name and logger.thread_name"
    (let [{:keys [logger]} (parse-string (with-out-str (log/info "dumb log")))]
      (is (= "json-logger" (:name logger)))
      (is (not-empty (:thread_name logger))))))

(deftest format-string-odd-no-crash
  (testing "When logging with the format variant and an odd number of additional arguments, it handles gracefully"
    (let [{:keys [args message]} (parse-string (with-out-str (log/infof "%s %d%% ready" "Upload" 50 :role)))]
      (is (= "Upload 50% ready" message))
      (is (= ["role"] args)))))

(deftest exception-stacktrace-java
  (testing "When there's an exception, error data is given in :stack :message :kind"
    (let [{:keys [error level]} (parse-string (with-out-str (log/info (Exception. "poks") "Error")))
          {:keys [stack message kind]} error
          stacktrace-lines (-> stack (string/split #"\n"))]
      (is (= level "error"))
      (is (= "poks" message))
      (is (= "java.lang.Exception" kind))
      (is (not-empty stacktrace-lines))
      (is (= "java.lang.Exception: poks" (last stacktrace-lines))))))

(deftest exception-ex-info-data-serializable
  (testing "When there's a clojure exception info throwned with data, the serializable data is available for inspection and the log level is error"
    (let [data {:a 10}
          {:keys [error level]} (parse-string (with-out-str (log/info (ex-info "poks" data))))
          {:keys [stack message kind map]} error]
      (is (= level "error"))
      (is (= "poks" message))
      (is (= "clojure.lang.ExceptionInfo" kind))
      (is (= data (:data map))))))

(deftest just-a-map
  (testing "When logging only a map object, make it the args structure"
    (let [{:keys [args]} (parse-string (with-out-str (log/info {:status 200 :duration 5})))]
      (is (= 200 (-> args :status)))
      (is (= 5 (-> args :duration))))))

(deftest a-msg-and-map
  (let [{:keys [args message]} (parse-string (with-out-str (log/info "a msg" {:status 200 :duration 5})))]
    (is (= "a msg" message))
    (is (= {:duration 5
            :status 200} args))))

(deftest a-msg-and-two-map
  (let [{:keys [args message]} (parse-string (with-out-str (log/info "a msg" {:status 200 :duration 5} {:status 300 :duration 10})))]
    (is (= [{:duration 5
             :status 200}
            {:duration 10
             :status 300}] args))
    (is (= "a msg" message))))

(deftest a-msg-and-a-vector-of-map
  (let [{:keys [args message level]} (parse-string (with-out-str (log/info "a msg" [{:status 200 :duration 5}])))]
    (is (= "info" level))
    (is (= [{:status 200 :duration 5}] (first args)))
    (is (= "a msg" message))))

(deftest a-warn-log
  (testing "When logging is warn, level is warn"
    (let [{:keys [args message level]} (parse-string (with-out-str (log/warn "Scary stuff" [{:status 404 :duration 5}])))]
      (is (= "warn" level))
      (is (= [{:status 404 :duration 5}] (first args)))
      (is (= "Scary stuff" message)))))


