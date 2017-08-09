(ns devops15.routes
  (:require [clojure.java.io :as io]
            [compojure.core :refer [ANY GET PUT POST DELETE routes]]
            [compojure.route :refer [resources]]
            [ring.util.response :refer [response]]
            [org.httpkit.server :refer [with-channel on-receive on-close send! websocket?]]))

(defn web-soc-handler [req]
  (with-channel req channel
    (on-close channel (fn [state]
                        (println "channel closed")))
    (if (websocket? channel)
      (println "websocket channel")
      (println "HTTP channel"))
    (on-receive channel (fn [data]
                          (send! channel data)))))

(defn home-routes [endpoint]
  (routes
   (GET "/" _
     (-> "public/index.html"
         io/resource
         io/input-stream
         response
         (assoc :headers {"Content-Type" "text/html; charset=utf-8"})))
   (GET "/ws" [] web-soc-handler)
   (resources "/")))
