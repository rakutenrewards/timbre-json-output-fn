(ns curbside.timbre-json-output-fn.core
  (:require
   [jsonista.core :as json]
   [taoensso.timbre :as log]
   [taoensso.timbre.appenders.core :refer [spit-appender]]
   [timbre-json-appender.core :as tjs]))

(defn- even-keywords?
  "Verifies if every 2 argument is a keyword.
  For instance `(log/info :keyword1 10 :keyword2 30)`"
  [vargs]
  (every? keyword? (take-nth 2 vargs)))

(defn- format-args
  "Handles formatting :args according to the variable args passed to the logging statement"
  [args]
  (cond
    ;; special case when remaining args is only a map, makes it easy to query in log engine
    (and (= 1 (count args)) (-> args first map?)) (first args)
    ;; when remaining args is a keyword/value sequence, turn the variable-args into a map
    (and (even? (count args)) (even-keywords? args)) (apply hash-map args)
    ;; otherwise just pass the structure as-is without manipulation
    :else args))

(defn- handle-vargs
  "Handles formatting :msg and :args"
  [?msg-fmt vargs]
  (cond
    ?msg-fmt (let [format-specifiers (tjs/count-format-specifiers ?msg-fmt)]
               {:message (String/format ?msg-fmt (to-array (take format-specifiers vargs)))
                :args (format-args (drop format-specifiers vargs))})
    :else (let [first-arg (first vargs)
                first-arg-string? (string? first-arg)]
            (cond-> {:args (format-args (if first-arg-string? (rest vargs) vargs))}
              first-arg-string? (assoc :message first-arg)))))

(def ^:private object-mapper (tjs/object-mapper {:pretty false}))

(defn- stacktrace-text
  "Logs textual representation of a stacktrace.
  Turn off colors in output by passing empty dict to stacktrace-fonts
  https://github.com/ptaoussanis/timbre#disabling-stacktrace-colors"
  [err]
  (log/stacktrace err {:stacktrace-fonts {}}))

(defn format-error
  "Formats error according to datadog's best practices.
  In addition, it contains a :map attribute for further inspection of the error"
  [err]
  (let [exception-map (Throwable->map err)]
    {:stack (stacktrace-text err)
     :message (.getMessage err)
     :kind (-> exception-map :via first :type)
     :map exception-map}))

(defn- json-output-log-map
  "Creates a log map for json serialization
  snake case on the keys is on purpose
  refer to datadog best practices (readme)"
  [logger-name {:keys [instant level ?ns-str ?file ?line ?err vargs ?msg-fmt]}]
  (let [message-args (handle-vargs ?msg-fmt
                                   vargs)
        log-map (cond-> (merge message-args
                               {:timestamp instant
                                :logger {:name logger-name
                                         :thread_name (.getName (Thread/currentThread))}
                                :level level
                                :ns ?ns-str
                                :file_line (str ?file ":" ?line)
                                :file ?file
                                :line ?line})
                  ?err (assoc
                         :level "error"
                         :error (format-error ?err)))]
    log-map))

(defn make-json-output-fn
  "Creates an output fn that will log in json
This was sourced from the timbre-json-appender plugin
and modified to remove the side-effect (println) so
that it can be used as an output-fn and all appenders
can benefit from it"
  ([logger-name] (fn [args] (json/write-value-as-string (json-output-log-map logger-name args) object-mapper)))
  ([] (make-json-output-fn "json-logger")))
