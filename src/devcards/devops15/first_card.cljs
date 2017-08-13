(ns devops15.first-card
  (:require-macros
   [devcards.core :refer [defcard-doc
                          defcard-rg
                          mkdn-pprint-source]])
  (:require
   [devcards.core]
   [reagent.core :as reagent]))


(defonce app-state (reagent/atom {:count 0}))

(defn inc2 [i]
  (+ i 2))

(defn on-click [ratom]
  (swap! ratom update-in [:count] inc2))

(defn counter [ratom]
  (let [count (:count @ratom)]
    [:div
     [:p "Current count: " count]
     [:button
      {:on-click #(on-click ratom)}
      "Increment"]]))

(defcard-rg counter
  [counter app-state]
  app-state
  {:inspect-data true})
