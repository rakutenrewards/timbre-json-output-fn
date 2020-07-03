(ns curbside.timbre-json-output-fn.core
  (:require
   [jsonista.core :as json]
   [taoensso.timbre :as log]
   [taoensso.timbre.appenders.core :refer [spit-appender]]
   [timbre-json-appender.core :as tjs]))

(defn even-keywords?
  "Verifies if every 2 argument is a keyword.
  For instance `(log/info :keyword1 10 :keyword2 30)`"
  [vargs]
  (every? keyword? (take-nth 2 vargs)))

(defn format-args
  "Handles formatting :args according to the variable args passed to the logging statement"
  [args]
  (cond
    ;; special case when remaining args is only a map, makes it easy to query in log engine
    (and (= 1 (count args)) (-> args first map?)) (first args)
    ;; when remaining args is a keyword/value sequence, turn the variable-args into a map
    (and (even? (count args)) (even-keywords? args)) (apply hash-map args)
    ;; otherwise just pass the structure as-is without manipulation
    :else args))

(defn handle-vargs
  "Handles formatting :msg and :args"
  [?msg-fmt vargs]
  (cond
    ?msg-fmt (let [format-specifiers (tjs/count-format-specifiers ?msg-fmt)]
               {:msg (String/format ?msg-fmt (to-array (take format-specifiers vargs)))
                :args (format-args (drop format-specifiers vargs))})
    :else (let [first-arg (first vargs)
                first-arg-string? (string? first-arg)]
            (cond-> {:args (format-args (if first-arg-string? (rest vargs) vargs))}
              first-arg-string? (assoc :msg first-arg)))))

(def ^:private object-mapper (tjs/object-mapper {:pretty false}))

(defn json-output-log-map
  "Creates a log map for json serialization"
  [{:keys [instant level ?ns-str ?file ?line ?err vargs ?msg-fmt]}]
  (let [msg-args (handle-vargs ?msg-fmt
                               vargs)
        log-map (cond-> (merge msg-args
                               {:timestamp instant
                                :level level
                                :thread (.getName (Thread/currentThread))
                                :ns ?ns-str
                                :file-line (str ?file ":" ?line)
                                :file ?file
                                :line ?line})
                  ?err (assoc
                         :level "error"
                         :err (Throwable->map ?err)
                         :stacktrace (log/stacktrace ?err {:stacktrace-fonts {}})))]
    log-map))

(defn json-output-fn
  "Creates an output fn that will log in json
This was sourced from the timbre-json-appender plugin
and modified to remove the side-effect (println) so
that it can be used as an output-fn and all appenders
can benefit from it"
  [args]
  (json/write-value-as-string (json-output-log-map args) object-mapper))
