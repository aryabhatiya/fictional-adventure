(ns devops15.todo-app
  (:require-macros
   [devcards.core :refer [defcard-doc
                          defcard-rg
                          deftest
                          mkdn-pprint-source]])
  (:require
   [datascript.core :as d]
   [devcards.core]
   [reagent.core :as reagent]
   [posh.reagent :as p]
   [cljs.test    :as t :refer-macros [is are testing]]))


(defn pairmap [pair] (apply merge (map (fn [[a b]] {a b}) pair)))

(defn ents [db ids] (map (partial d/entity db) ids))

(defn new-entity! [conn varmap]
  ((:tempids (d/transact! conn [(merge varmap {:db/id -1})])) -1))

;;; setup

(def tempid (let [n (atom 0)] (fn [] (swap! n dec))))

(def schema {:task/category         {:db/valueType :db.type/ref}
             :category/todo         {:db/valueType :db.type/ref}
             :todo/display-category {:db/valueType :db.type/ref}
             :task/name             {:db/unique :db.unique/identity}
             :todo/name             {:db/unique :db.unique/identity}
             :action/editing        {:db/cardinality :db.cardinality/many}})

(def conn (d/create-conn schema))

(defn populate! [conn]
  (let [todo-id (new-entity! conn {:todo/name "Matt's List" :todo/listing :all})
        at-home    (new-entity! conn {:category/name "At Home" :category/todo todo-id})
        work-stuff (new-entity! conn {:category/name "Work Stuff" :category/todo todo-id})
        hobby      (new-entity! conn {:category/name "Hobby" :category/todo todo-id})]
    (d/transact!
     conn
     [{:db/id (tempid)
       :task/name "Clean Dishes"
       :task/done true
       :task/category at-home}
      {:db/id (tempid)
       :task/name "Mop Floors"
       :task/done true
       :task/pinned true
       :task/category at-home}
      {:db/id (tempid)
       :task/name "Draw a picture of a cat"
       :task/done false
       :task/category hobby}
      {:db/id (tempid)
       :task/name "Compose opera"
       :task/done true
       :task/category hobby}
      {:db/id (tempid)
       :task/name "stock market library"
       :task/done false
       :task/pinned true
       :task/category work-stuff}])))

(populate! conn)

(p/posh! conn)

(defn testdog [conn]
  (let [floors @(p/pull conn '[*] [:task/name "Mop Floors"])]
    [:div
     {:on-click
      #(p/transact! conn [[:db/add (:db/id floors) :task/done (not (:task/done floors))]])}
     "Hey guys"
     (pr-str floors)
     ]))

(defcard-rg testdog
  [testdog conn])


(defn start [conn]
  (let [todo-id (d/q '[:find ?todo . :where [?todo :todo/name _]] @conn)]
    [:div (pr-str todo-id)]))

(defcard-rg start
  [start conn])

(defn dashboard-button [conn todo-id]
  (let [current-category (-> @(p/pull conn [:todo/display-category] todo-id)
                             :todo/display-category
                             :db/id)]
    [:button
     {:on-click #(p/transact!
                  conn
                  (if current-category
                    [[:db/retract todo-id :todo/display-category current-category]
                     [:db/add todo-id :todo/listing :all]]
                    []))}
     "Dashboard"]))

(defn app2 [conn todo-id]
  (let [todo @(p/pull conn '[:todo/name] [:todo/name "Matt's List"])]
    [:div
     [:h1 (:todo/name todo)]
     [dashboard-button conn todo-id]]))

(defn start2 [conn]
  (let [todo-id (d/q '[:find ?todo . :where [?todo :todo/name _]] @conn)]
    [app2 conn todo-id]))

(defcard-rg start2
  [start2 conn])


(defn dash-task [conn task-id]
  (let [task @(p/pull conn '[:db/id :task/done :task/pinned :task/name
                             {:task/category [:db/id :category/name]}]
                      task-id)]
    [:span
     [:div  (:task/name task) ]
     [:div  (:category/name (:task/category task)) ]
     [:div (if (:task/done task) "done" "not-done")]]))

(defn task-list [conn todo-id]
  (let [listing (-> @(p/pull conn [:todo/listing] todo-id)
                    :todo/listing)
        tasks   (case listing
                  :all     @(p/q '[:find [?t ...]
                                   :in $ ?todo
                                   :where
                                   [?c :category/todo ?todo]
                                   [?t :task/category ?c]]
                                 conn todo-id)
                  @(p/q '[:find [?t ...]
                          :in $ ?todo ?done
                          :where
                          [?c :category/todo ?todo]
                          [?t :task/category ?c]
                          [?t :task/done ?done]]
                        conn todo-id (= listing :done)))]
    [:div
     [:h3 (case listing
            :all "All Tasks"
            :done "Completed Tasks"
            :not-done "Uncompleted Tasks")]
     (if-not (empty? tasks)
       [:div
        (for [t tasks]
          ^{:key t} [:div [dash-task conn t]])
        ]
       [:div "None"])]))

(defcard-rg task-list
  [task-list conn (d/q '[:find ?todo . :where [?todo :todo/name _]] @conn)])

(defn change-listing! [conn todo-id v]
  (p/transact! conn [[:db/add todo-id :todo/listing v]]))

(defn listing-buttons [conn todo-id]
  [:div
   [:button
    {:on-click #(change-listing! conn todo-id :all)}
    "All"]
   [:button
    {:on-click #(change-listing! conn todo-id :done)}
    "Checked"]
   [:button
    {:on-click #(change-listing! conn todo-id :not-done)}
    "Un-checked"]])

(defcard-rg listing-buttons
  [listing-buttons conn (d/q '[:find ?todo . :where [?todo :todo/name _]] @conn)])

(defn dashboard [conn todo-id]
  (let [cats (->> @(p/pull conn
                           '[{:category/_todo [:db/id :category/name {:task/_category [:db/id]}]}]
                           todo-id)
                  :category/_todo
                  (sort-by :category/name))]
    [:div
     [:h2 "DASHBOARD: "] [listing-buttons conn todo-id]
     [task-list conn todo-id]]))

(defcard-rg deshboard
  [dashboard conn 1])


(defn add-box [conn add-fn]
  (let [edit (reagent/atom "")]
    (fn [conn add-fn]
      [:span
       [:input
        {:type "text"
         :value @edit
         :onChange #(reset! edit (-> % .-target .-value))}]
       [:button
        {:on-click #(when-not (empty? @edit)
                     (add-fn @edit)
                     (reset! edit ""))}
        "Add"]])))

;;;;; edit box

(defn edit-box [conn edit-id id attr]
  (let [edit @(p/pull conn [:edit/val] edit-id)]
    [:span
     [:input
      {:type "text"
       :value (:edit/val edit)
       :onChange #(p/transact! conn [[:db/add edit-id :edit/val (-> % .-target .-value)]])}]
     [:button
      {:on-click #(p/transact! conn [[:db/add id attr (:edit/val edit)]
                                    [:db.fn/retractEntity edit-id]])}
      "Done"]
     [:button
      {:on-click #(p/transact! conn [[:db.fn/retractEntity edit-id]])}
      "Cancel"]]))

(defn editable-label [conn id attr]
  (let [val  (attr @(p/pull conn [attr] id))
        edit @(p/q '[:find ?edit .
                     :in $ ?id ?attr
                     :where
                     [?edit :edit/id ?id]
                     [?edit :edit/attr ?attr]]
                   conn id attr)]
    (if-not edit
      [:span val
       [:button
        {:on-click #(new-entity! conn {:edit/id id :edit/val val :edit/attr attr})}
        "Edit"]]
      [edit-box conn edit id attr])))

;;; check box

(defn checkbox [conn id attr checked?]
  [:input
      {:type "checkbox"
       :checked checked?
       :onChange #(p/transact! conn [[:db/add id attr (not checked?)]])}])

;; stage button

(defn stage-button [stages finish-fn]
  (let [stage (reagent/atom 0)]
    (fn [stages finish-fn]
      (when (= @stage (count stages))
        (do (finish-fn)
            (reset! stage 0)))
      [:button
       {:on-click    #(swap! stage inc)
        :onMouseOut #(reset! stage 0)}
       (nth stages @stage)])))

(defn category-item [conn todo-id category]
  [:button
   {:on-click #(p/transact!
                conn
                [[:db/add todo-id :todo/display-category (:db/id category)]])}
   (:category/name category)
   " (" (count (:task/_category category)) ")"])



(defn category-menu [conn todo-id]
  (let [cats (->> @(p/pull conn
                           '[{:category/_todo [:db/id :category/name {:task/_category [:db/id]}]}]
                           todo-id)
                  :category/_todo
                  (sort-by :category/name))]
    [:span
     (for [c cats]
       ^{:key (:db/id c)}
       [category-item conn todo-id c])]))

(defcard-rg category-menu
  [category-menu conn 1])

(defn category-menu2 [conn todo]
  (let [cats (d/q '[:find ?n (count ?tn) :where
                    [?e :category/todo]
                    [?e :category/name ?n]
                    [?tn :task/category ?e]
                    ] (d/db conn))]
    [:div
     (pr-str cats)]))

(defcard-rg category-menu2
  [category-menu2 conn 1])
