(ns devops15.datascript.entry
  (:require-macros
   [devcards.core :refer [defcard-doc
                          defcard-rg
                          deftest
                          mkdn-pprint-source]])
  (:require
   [cljs.reader :as edn]
   [datascript.core :as d]
   [devcards.core]
   [reagent.core :as reagent]
   [cljs.test    :as t :refer-macros [is are testing]]))

(deftest test-entity
  (let [db (-> (d/empty-db {:aka {:db/cardinality :db.cardinality/many}})
               (d/db-with [{:db/id 1, :name "Ivan", :age 19, :aka ["X" "Y"]}
                           {:db/id 2, :name "Ivan", :sex "male", :aka ["Z"]}
                           [:db/add 3 :huh? false]]))
        e  (d/entity db 1)]
    (is (= (:db/id e) 1))
    (is (identical? (d/entity-db e) db))
    (is (= (:name e) "Ivan"))
    (is (= (e :name) "Ivan")) ; IFn form
    (is (= (:age  e) 19))
    (is (= (:aka  e) #{"X" "Y"}))
    (is (= true (contains? e :age)))
    (is (= false (contains? e :not-found)))
    (is (= (into {} e)
           {:name "Ivan", :age 19, :aka #{"X" "Y"}}))
    (is (= (into {} (d/entity db 1))
           {:name "Ivan", :age 19, :aka #{"X" "Y"}}))
    (is (= (into {} (d/entity db 2))
           {:name "Ivan", :sex "male", :aka #{"Z"}}))
    (let [e3 (d/entity db 3)]
      (is (= (into {} e3) {:huh? false})) ; Force caching.
      (is (false? (:huh? e3))))

    (is (= (pr-str (d/entity db 1)) "{:db/id 1}"))
    (is (= (pr-str (let [e (d/entity db 1)] (:unknown e) e)) "{:db/id 1}"))
    ;; read back in to account for unordered-ness
    (is (= (edn/read-string (pr-str (let [e (d/entity db 1)] (:name e) e)))
           (edn/read-string "{:name \"Ivan\", :db/id 1}")))))
