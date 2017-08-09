(ns devops15.core
  (:require [reagent.core :as reagent :refer [atom]]))

(enable-console-print!)


(defonce app-state (reagent/atom {:text "Hello Chestnut!"
                                  :ws-text ""}))

(def ws (js/WebSocket. "ws://localhost:10555/ws"))
(aset ws "onopen" (fn [event]
                    (.send ws "hello world")))
(aset ws "onmessage" (fn [event]
                       (swap! app-state assoc :ws-text (.-data event))))

(defn greeting []
  [:div
   [:input {:type "text"
            :on-change #(.send ws (str "remote " (.. % -target -value)))} ]
   [:h1 (:text @app-state)]
   [:h1 (:ws-text @app-state)]])

(defn render []
  (reagent/render [greeting] (js/document.getElementById "app")))
