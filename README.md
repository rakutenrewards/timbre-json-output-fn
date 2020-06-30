# timbre-json-output-fn

A library for structured json logging for easy integration with log
 engine such as [datadog], AWS cloudwatch, GCP stackdriver. 
 
 Inspired
 by [timbre-json-appender](https://github.com/viesti/timbre-json-appender) and
 improves on it in a backward compatible way and makes it more robust and
  consistent. 
  
 > Note that this library provides an `output-fn` instead of an `appender`. This
  makes it more flexible to integrate with existing appenders configured in a
    service. Since this implementation is a pure function returning the
 structure, is is possible to combine json logging and pipe it into any
 appenders such as standard output or text file.
 
In summary, what this lib does:
 - `:msg` is included only when the first argument is a string. This covers
  most existing logging scenarios.
   ```
   (log/info "HTTP Request")
   (log/infof "HTTP %s" "Request")
   ```  
    will both yield `{:msg "HTTP Request" ...}`
 - `:file-line` is given as a parameter to easily segment the log
  origin from other logs. `{:file-line  "path/to/file:35" ...}`
 - `:args` is a map meant to be easily queryable by the log engine. There's a
  couple of ways to accomplish this: 
   ```
   (log/info :status 200 :duration 10)
   (log/info "HTTP Request" :status 200 :duration 10)
   (log/info "HTTP Request" {:status 200 :duration 10})
   (log/info {:status 200 :duration 10})
   ```
   will all yield `{:args {:status 200 :duration 10} ...}`
 - When an exception occurs, the structured stacktrace contained in `:err` is
  useful for introspecting data and underlying causes of the stacktrace. When
   exception occured from an `ex-info`, the data provided is also available
    in the structure. It is also often easier to read the textual version now 
    contained in `:stacktrace`. 

## Examples

```
user=> (log/info "Task done" :duration 5)
{:args {:duration 5}
 :file "[...]/test/curbside/timbre_json_logs/core_test.clj"
 :file-line "[...]/test/curbside/timbre_json_logs/core_test.clj:28"
 :line 28
 :msg "Task done"
 :ns "curbside.timbre-json-output-fn.core-test"}
```

See [tests](test/curbside/timbre_json_logs) for a rundown of the spec
 
## Deployment

Deployments are automatically deployed to 
[github packages](https://github.com/curbside/timbre-json-output-fn/packages) by 
[github actions](.github/workflows/deploy.yml) for both snapshots and
 releases upon pushing to the master branch.
 
### Release

To trigger a release version increase in the project
```
lein release
```

## Require this lib

To include this library in a dependent project, include the following in the
 `:repositories` of the `project.clj`

```clojure
:repositories [["RakutenReady/timbre-json-output-fn"
                {:url "https://maven.pkg.github.com/RakutenReady/timbre-json-output-fn"
                 :username [:gpg :env/github_actor]
                 :password [:gpg :env/github_token]}]]
``` 
