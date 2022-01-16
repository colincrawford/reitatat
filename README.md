# Reitatat

### Project Status

**Alpha** - Still figuring out the scope and API of the project

---

**Easy to use Clojure server micro-framework**

Built on the shoulders of giants

- Very performant routing via the amazing [Reitit](https://github.com/metosin/reitit) router
- Rock solid web server - [Undertow](https://undertow.io/) - via the [luminus ring adapter](https://github.com/luminus-framework/ring-undertow-adapter)
- Generates [swagger documentation](https://cljdoc.org/d/metosin/reitit/0.5.15/doc/ring/swagger-support)

### TODO

- [ ] Response utilities?
- [ ] Database access? (Hugsql?)
- [ ] Configurability of the Reitit middleware?

### Getting Started

Example using [spec](https://clojure.org/guides/spec) for validation

```clj
(ns cool-service.server
  (:require [reitatat.server :as rtts]
            [clojure.spec.alpha :as s]))

;; Simple request handler that just returns the
;; keys in the request map passed to the handler
(defn request-keys-handler
  [request]
  {:status 200
   :body {:keys (keys request)}})

;; Slightly more complicated request handler destructuring a query
;; param out of the request map (parameters is determined by the
;; :parameters key passed in the route definition)
(defn greeting-handler
  [{{{:keys [name]} :query} :parameters}]
  (let [to-greet (if (empty? name) "Anonymous" name)
        greeting (str "Hey " to-greet "!")]
    {:status 200
     :body {:greeting greeting}}))

;; Struct validation used in the greeting route definition
(s/def ::name (s/nilable string?))
(s/def ::greeting-query (s/keys :req-un [::name]))

;; Create a Ring compliant handler function using Reitit route definitions
(def handler
  (rtts/make-handler
   [["/request-keys" {:get request-keys-handler}]
    ["/greeting" {:get {:parameters {:query ::greeting-query}
                   :responses {200 {:body {:greeting string?}}}
                   :handler #'hey-handler}}]]))
(defn create
  [port]
  ;; We pass a reference to the handler so that we can reevaluate the handler
  ;; with updates in the REPL and it will automatically get reflected in the
  ;; running server
  (let [server (rtts/make-server #'handler {:port port})]
    (do (println "Server listenning on port " port)
        server)))

;; Useful functions when developing with the REPL
(defn start [s] (rtts/restart-server s))
(defn stop [s] (rtts/stop-server s))

(defn main
  [& args]
  (create))
```
