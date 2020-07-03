(defproject curbside/timbre-json-output-fn "0.1.2-SNAPSHOT"
  :description "Standardized structured logging for datadog"
  :url "http://github.com/RakutenReady/timbre-json-output-fn"

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [com.taoensso/timbre "4.10.0"]
                 [viesti/timbre-json-appender "0.1.0"]
                 [metosin/jsonista "0.2.6"]]

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "v" "--no-sign"]
                  ["vcs" "push"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]

  :repositories [["github" {:url "https://maven.pkg.github.com/RakutenReady/timbre-json-output-fn"
                            :username :env/github_actor
                            :password :env/github_token
                            :sign-releases false}]])
