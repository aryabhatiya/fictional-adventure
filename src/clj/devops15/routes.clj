(ns devops15.routes
  (:require [clojure.java.io :as io]
            [compojure.core :refer [ANY GET PUT POST DELETE routes]]
            [compojure.route :refer [resources]]
            [ring.util.response :refer [response]]
            [org.httpkit.server :refer [with-channel on-receive on-close send! websocket?]]
            [cognitect.transit :as transit])
  (import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(defn web-soc-handler [req]
  (with-channel req channel
    (on-close channel (fn [state]
                        (println "channel closed")))
    (if (websocket? channel)
      (println "websocket channel")
      (println "HTTP channel"))
    (on-receive channel (fn [data]
                          (send! channel data)))))


(defn average [req]
  (with-channel req channel
    (on-close channel (fn [state]
                        (println "channel closed")))
    (if (websocket? channel)
      (println "websocket channel avg")
      (println "HTTP channel avg"))
    (on-receive channel (fn [data]
                          (let [stream (ByteArrayInputStream. (.getBytes data))]
                            (send! channel (str (apply + (transit/read
                                                          (transit/reader stream :json))))))))))

(defn home-routes [endpoint]
  (routes
   (GET "/" _
     (-> "public/index.html"
         io/resource
         io/input-stream
         response
         (assoc :headers {"Content-Type" "text/html; charset=utf-8"})))
   (GET "/ws" [] web-soc-handler)
   (GET "/avg" [] average)
   (resources "/")))
