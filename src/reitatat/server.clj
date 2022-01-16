(ns reitatat.server
  (:require [reitit.ring :as ring]
            [reitit.coercion.spec]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.ring.coercion :as coercion]
            [reitit.dev.pretty :as pretty]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.multipart :as multipart]
            [reitit.ring.middleware.parameters :as parameters]
            ;; Uncomment to use
            ; [reitit.ring.middleware.dev :as dev]
            [muuntaja.core :as m]
            ;; Web server to run with the ring router
            [ring.adapter.undertow :refer [run-undertow]]))

(defn- internal-error-handler
  [^Exception error req]
  {:status 500
   :body {:message "Internal Server Error"}})

(defn- bad-request-handler
  [^Exception error req]
  {:status 400
   :body {:message "Invalid Request"}})

(defn- make-error-handlers
  [{:keys [default-error-handler
           req-coercion-error
           res-coercion-error]
    :or {default-error-handler (partial exception/wrap-log-to-console
                                  internal-error-handler)
         req-coercion-error bad-request-handler
         res-coercion-error internal-error-handler}}]
  {::exception/default default-error-handler
   :reitit.coercion/request-coercion req-coercion-error
   :reitit.coercion/response-coercion res-coercion-error})

(defn- make-swagger-routes
  "Generates a route to return the app's swagger definition"
  [{:keys [title description path ui-path]
    :or {title "swagger"
         description "swagger api"
         path "/swagger.json"
         ui-path "/api-docs/*"}}]
  [[path
    {:get {:no-doc true
           :swagger {:info {:title title
                            :description description}}
           :handler (swagger/create-swagger-handler)}}]
   [ui-path
    {:get {:no-doc true
           :handler
           (swagger-ui/create-swagger-ui-handler
            {:url path
             :config {:validatorUrl nil
                      :operationsSorter "alpha"}})}}]])

(defn make-handler
  "Create a ring handler that uses a Reitit router

   routes - Reitit routes vector
   options
   | key                 | description |
   | `:swagger`          | swagger options
   | `:error-handlers`   | map with key to handler function taking [error request] as arguments
   |                     | keys: `:default-error-handler`, `:req-coercion-error`, `:res-coercion-error`
   | `:default-handlers` | map with key to ring handler functions for unhandled routes
   |                     | keys: `:not-found`, `:not-acceptable`, `:method-not-allowed`
  "
  ([routes]
   (make-handler routes {}))
  ([routes {:keys [swagger error-handlers default-handlers]}]
   (let [swagger-routes (make-swagger-routes swagger)
         app-routes (into swagger-routes routes)
         error-handlers (make-error-handlers error-handlers)
         default-handler (ring/create-default-handler default-handlers)
         exception-middleware (exception/create-exception-middleware
                               (merge exception/default-handlers
                                      error-handlers))]
     (ring/ring-handler
      (ring/router
       app-routes
       {;;:reitit.middleware/transform dev/print-request-diffs ;; pretty diffs
        ;;:validate spec/validate ;; enable spec validation for route data
        ;;:reitit.spec/wrap spell/closed ;; strict top-level validation
        :exception pretty/exception
        :data {:coercion reitit.coercion.spec/coercion
               :muuntaja m/instance
               :middleware [;; swagger feature
                            swagger/swagger-feature
                            ;; query-params & form-params
                            parameters/parameters-middleware
                            ;; content-negotiation, request decoding, and response encoding
                            muuntaja/format-middleware
                            ;; exception handling
                            exception-middleware
                            ;; coercing response bodys
                            coercion/coerce-response-middleware
                            ;; coercing request parameters
                            coercion/coerce-request-middleware
                            ;; coercion exceptions into responses
                            coercion/coerce-exceptions-middleware
                            ;; multipart
                            multipart/multipart-middleware]}})
      default-handler))))

(defn make-server
  "Creates and starts a Reitatat server"
  ([handler]
   (make-server handler {}))
  ([handler {:keys [port server swagger]
            :or   {port 8080}}]
   (let [server-opts (merge {:port port
                             :websocket? true
                             :session-manager false
                             :http2? true}
                            server)]
     (run-undertow handler server-opts))))

(defn restart-server
  "Starts a Reitatat server"
  [server]
  (do (.start server)
      server))

(defn stop-server
  "Stops a Reitatat server"
  [server]
  (do (.stop server)
      server))
