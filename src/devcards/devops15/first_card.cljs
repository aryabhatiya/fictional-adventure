(ns devops15.first-card
  (:require-macros
   [devcards.core :refer [defcard-doc
                          defcard-rg
                          deftest
                          mkdn-pprint-source]])
  (:require
   [datascript.core :as d]
   [datascript.db :as db]
   [devcards.core]
   [reagent.core :as reagent]
   [cljs.test    :as t :refer-macros [is are testing]]))



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
