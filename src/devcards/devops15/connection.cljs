(ns devops15.connection
  (:require-macros
   [devcards.core :refer [defcard-doc
                          defcard-rg
                          deftest
                          mkdn-pprint-source]])
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.reader :as reader]
            [cognitect.transit :as transit]))

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
   [:input {
            :type "text"
            :on-change #(swap! app-state
                               assoc
                               :input (.. % -target -value))
            :on-key-press #(if (= 13 (.-charCode %))
                             (do
                               (js/console.log  (:input @app-state))
                               (.send avg (transit/write write
                                                         (reader/read-string
                                                          (:input @app-state))))))}]
   [:h1 (:avg @app-state)]])

(defn greeting []
  [:div
   [:input {:type "text"
            :autoFocus true
            :on-change #(.send ws (str "remote " (.. % -target -value)))} ]
   [:h1 (:text @app-state)]
   [:h1 (:ws-text @app-state)]
   ])


(defcard-rg eval
  [eval app-state]
  app-state
  {:inspect-data true})

(defcard-rg greeting
  [greeting app-state]
  app-state
  {:inspect-data true})
