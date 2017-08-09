(ns devops15.core
  (:require [reagent.core :as reagent :refer [atom]]))

(enable-console-print!)

(def ws (js/WebSocket. "ws://localhost:10555/ws"))
(aset ws "onopen" (fn [event]
                    (.send ws "hello world")))
(aset ws "onmessage" (fn [event]
                       (js/console.log (.data event))))

(defonce app-state (atom {:text "Hello Chestnut!"}))

(defn greeting []
  [:h1 (:text @app-state)])

(defn render []
  (reagent/render [greeting] (js/document.getElementById "app")))
