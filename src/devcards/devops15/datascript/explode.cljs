(ns devops15.datascript.explode
  (:require-macros
   [devcards.core :refer [defcard-doc
                          defcard-rg
                          deftest
                          mkdn-pprint-source]])
  (:require
   [cljs.reader :as edn]
   [datascript.core :as d]
   [datascript.arrays :as da]
   [devcards.core]
   [reagent.core :as reagent]
   [cljs.test    :as t :refer-macros [is are testing]]))

(def Throwable js/Error)

(deftest test-explode
  (doseq [coll [["Devil" "Tupen"]
                #{"Devil" "Tupen"}
                '("Devil" "Tupen")
                (da/into-array ["Devil" "Tupen"])]]
    (testing coll
      (let [conn (d/create-conn { :aka { :db/cardinality :db.cardinality/many }
                                 :also { :db/cardinality :db.cardinality/many} })]
        (d/transact! conn [{:db/id -1
                            :name  "Ivan"
                            :age   16
                            :aka   coll
                            :also  "ok"}])
        (is (= (d/q '[:find  ?n ?a
                      :where [1 :name ?n]
                      [1 :age ?a]] @conn)
               #{["Ivan" 16]}))
        (is (= (d/q '[:find  ?v
                      :where [1 :also ?v]] @conn)
               #{["ok"]}))
        (is (= (d/q '[:find  ?v
                      :where [1 :aka ?v]] @conn)
               #{["Devil"] ["Tupen"]}))))))

(deftest test-explode-ref
  (let [db0 (d/empty-db { :children { :db/valueType :db.type/ref
                                      :db/cardinality :db.cardinality/many } })]
    (let [db (d/db-with db0 [{:db/id -1, :name "Ivan", :children [-2 -3]}
                             {:db/id -2, :name "Petr"}
                             {:db/id -3, :name "Evgeny"}])]
      (is (= (d/q '[:find ?n
                    :where [_ :children ?e]
                           [?e :name ?n]] db)
             #{["Petr"] ["Evgeny"]})))

    (let [db (d/db-with db0 [{:db/id -1, :name "Ivan"}
                             {:db/id -2, :name "Petr", :_children -1}
                             {:db/id -3, :name "Evgeny", :_children -1}])]
      (is (= (d/q '[:find ?n
                    :where [_ :children ?e]
                           [?e :name ?n]] db)
             #{["Petr"] ["Evgeny"]})))

    (is (thrown-with-msg? Throwable #"Bad attribute :_parent"
      (d/db-with db0 [{:name "Sergey" :_parent 1}])))))

(deftest test-explode-nested-maps
  (let [schema { :profile { :db/valueType :db.type/ref }}
        db     (d/empty-db schema)]
    (are [tx res] (= (d/q '[:find ?e ?a ?v
                            :where [?e ?a ?v]]
                          (d/db-with db tx)) res)
      [ {:db/id 5 :name "Ivan" :profile {:db/id 7 :email "@2"}} ]
      #{ [5 :name "Ivan"] [5 :profile 7] [7 :email "@2"] }

      [ {:name "Ivan" :profile {:email "@2"}} ]
      #{ [1 :name "Ivan"] [1 :profile 2] [2 :email "@2"] }

      [ {:profile {:email "@2"}} ] ;; issue #59
      #{ [1 :profile 2] [2 :email "@2"] }

      [ {:email "@2" :_profile {:name "Ivan"}} ]
      #{ [1 :email "@2"] [2 :name "Ivan"] [2 :profile 1] }
    ))

  (testing "multi-valued"
    (let [schema { :profile { :db/valueType :db.type/ref
                              :db/cardinality :db.cardinality/many }}
          db     (d/empty-db schema)]
      (are [tx res] (= (d/q '[:find ?e ?a ?v
                              :where [?e ?a ?v]]
                            (d/db-with db tx)) res)
        [ {:db/id 5 :name "Ivan" :profile {:db/id 7 :email "@2"}} ]
        #{ [5 :name "Ivan"] [5 :profile 7] [7 :email "@2"] }

        [ {:db/id 5 :name "Ivan" :profile [{:db/id 7 :email "@2"} {:db/id 8 :email "@3"}]} ]
        #{ [5 :name "Ivan"] [5 :profile 7] [7 :email "@2"] [5 :profile 8] [8 :email "@3"] }

        [ {:name "Ivan" :profile {:email "@2"}} ]
        #{ [1 :name "Ivan"] [1 :profile 2] [2 :email "@2"] }

        [ {:name "Ivan" :profile [{:email "@2"} {:email "@3"}]} ]
        #{ [1 :name "Ivan"] [1 :profile 2] [2 :email "@2"] [1 :profile 3] [3 :email "@3"] }

        [ {:email "@2" :_profile {:name "Ivan"}} ]
        #{ [1 :email "@2"] [2 :name "Ivan"] [2 :profile 1] }

        [ {:email "@2" :_profile [{:name "Ivan"} {:name "Petr"} ]} ]
        #{ [1 :email "@2"] [2 :name "Ivan"] [2 :profile 1] [3 :name "Petr"] [3 :profile 1] }
      ))))


(deftest test-circular-refs
  (let [schema {:comp {:db/valueType   :db.type/ref
                       :db/cardinality :db.cardinality/many
                       :db/isComponent true}}
        db     (d/db-with (d/empty-db schema)
                 [{:db/id 1, :comp [{:name "C"}]}])]
    (is (= (mapv (juxt :e :a :v) (d/datoms db :eavt))
           [ [ 1 :comp 2  ]
             [ 2 :name "C"] ])))

  (let [schema {:comp {:db/valueType   :db.type/ref
                       :db/cardinality :db.cardinality/many}}
        db     (d/db-with (d/empty-db schema)
                 [{:db/id 1, :comp [{:name "C"}]}])]
    (is (= (mapv (juxt :e :a :v) (d/datoms db :eavt))
           [ [ 1 :comp 2  ]
             [ 2 :name "C"] ])))

  (let [schema {:comp {:db/valueType   :db.type/ref
                       :db/isComponent true}}
        db     (d/db-with (d/empty-db schema)
                 [{:db/id 1, :comp {:name "C"}}])]
    (is (= (mapv (juxt :e :a :v) (d/datoms db :eavt))
           [ [ 1 :comp 2  ]
             [ 2 :name "C"] ])))

  (let [schema {:comp {:db/valueType   :db.type/ref}}
        db     (d/db-with (d/empty-db schema)
                 [{:db/id 1, :comp {:name "C"}}])]
    (is (= (mapv (juxt :e :a :v) (d/datoms db :eavt))
           [ [ 1 :comp 2  ]
             [ 2 :name "C"] ]))))
