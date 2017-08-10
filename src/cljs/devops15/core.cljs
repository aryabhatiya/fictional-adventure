(ns devops15.core
  (:require [reagent.core :as reagent :refer [atom]]
            [cognitect.transit :as transit]))

(enable-console-print!)

(def read (transit/reader :json))
(def write (transit/writer :json))

(defonce app-state (reagent/atom {:text "Hello Chestnut!"
                                  :ws-text ""}))

(def ws (js/WebSocket. "ws://localhost:10555/ws"))
(def avg (js/WebSocket. "ws://localhost:10555/avg"))

(aset ws "onopen" (fn [event]
                    (.send ws "hello world")))
(aset ws "onmessage" (fn [event]
                       (swap! app-state assoc :ws-text (.-data event))))

(aset avg "onopen" (fn [event]
                     (.send avg (transit/write write [1 2 3 4]))))

(aset avg "onmessage" (fn [event]
                        (js/console.log "avg: " (.-data event))
                        (swap! app-state assoc :avg (.-data event))))


(defn eval []
  [:div
   [:input {:type "text"
            :on-change #(swap! app-state
                               assoc
                               :input (.. % -target -value))
            :on-key-press #(if (= 13 (.-charCode %))
                             (do
                               (js/console.log  (:input @app-state))
                               (.send avg (transit/write write
                                                         (cljs.reader/read-string
                                                          (:input @app-state))))))}]
   [:h1 (:avg @app-state)]])

(defn greeting []
  [:div
   [:input {:type "text"
            :on-change #(.send ws (str "remote " (.. % -target -value)))} ]
   [:h1 (:text @app-state)]
   [:h1 (:ws-text @app-state)]
   [eval]])

(defn render []
  (reagent/render [greeting] (js/document.getElementById "app")))
