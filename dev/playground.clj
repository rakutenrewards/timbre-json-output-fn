(ns playground
  "Some examples showing how to integrate with
  threads, futures and uncaught exceptions.

  Run examples with lein to get a feel of the outputs. Pipe into `jq` to parse logs!
  lein run -m playground/sequence-reel | jq '.args.index'
  lein run -m playground/throw-log-errors | jq '.level'

  There is also a minimal http server example. Try:
  lein run -m playground/minimal-http-server 2> /dev/null | jq '.'
  ...then:
  curl http://localhost:9999/foo
  curl http://localhost:9999/kaboom

  Logs are also appended to tmp.log so you can always go back and inspect with
  cat tmp.log | jq '.'"
  (:require
   [taoensso.timbre :as log]
   [taoensso.timbre.appenders.core :refer [spit-appender]]
   [curbside.timbre-json-output-fn.core :refer [make-json-output-fn]]
   [compojure.core :refer [GET routes wrap-routes]]
   [org.httpkit.server :as server]
   [ring.logger :as logger]))

(log/merge-config!
 {:appenders {:println {:enabled? true}
              :spit (spit-appender {:fname "tmp.log"})}
  :output-fn (make-json-output-fn)})

(defn sequence-reel
  "Simple program that would output some logs at intervals
  :index acccessible as data by the log consumer"
  []
  (doseq [i (range 20)]
    (log/infof "Run %d" i {:index i})
    (Thread/sleep 500)))

(defn throw-log-errors
  "Timbre comes with a log-errors macro
  which will ensure proper logging of an
  exception happening in the main thread"
  []
  (log/log-errors
   (log/info "I'm about to do some dangerous arithmetic but still be logged gracefully...")
   (/ 0)))

(defn throw-in-future
  "Timbre also comes with a logged-future macro
  which will ensure proper logging in the context of running futures"
  []
  (log/logged-future
   (let [the-future (future (log/info "It's lonely here")
                            (throw (ex-info "There's no one left" {:population 0})))]
     (try
       @the-future
       (finally
         (shutdown-agents))))))

(defn throw-uncaught-thread
  "If doing traditional java threads,
  timbre comes with an handle-uncaught-jvm-exceptions!
  helper to catch those exceptions"
  []
  (log/handle-uncaught-jvm-exceptions!)
  (.start (Thread. (fn []
                     (throw (Exception. "Inner thread exception!!!"))))))

(defn minimal-http-server
  "Minimal http server example
  showing how to wrap the requests
  with an http access logger."
  []
  (let [app (-> (routes
                 (GET "/kaboom" [] (fn [request] (/ 0)))
                 (GET "/foo" [] (fn [request] (log/info "foo")
                                  {:status 200
                                   :headers {}
                                   :body "Foo"})))
                (logger/wrap-log-response {:request-keys [:request-method :uri :query-string :server-name :remote-addr]
                                           :log-fn (fn [{:keys [level throwable message]}]
                                                     (if throwable
                                                       (log/log :error throwable)
                                                       (log/log level "http-access-log" :message message)))}))]
    (log/info "Starting server")
    (server/run-server app {:port 9999})))
