(ns devops15.datamic
  (:require
   [clojure.test :refer :all]
   [com.stuartsierra.component :as component]
   [system.components.datomic :refer [new-datomic-db]]
   [datomic.api :as d]
   [clojure.pprint :refer [pprint]]))


(defn todo-new
  [title]
  [{:todo/title        title
    :todo/completed?   false}])

(defn todo-count
  [db]
  (count
   (d/q '[:find ?t
          :where [_ :todo/title ?t]]
        db)))

(deftest example-passing-test
  (is (= 1 1)))

(defrecord Datomic-Test [uri conn schema]
  component/Lifecycle
  (start [component]
    (let [uri (str "datomic:mem://" (gensym))
          db (d/create-database uri)
          conn (d/connect uri)
          ]
      @(d/transact conn schema)
      (-> component
          (assoc  :conn conn)
          (assoc  :uri uri)
          (assoc :db (d/db conn)))))
  (stop [component]
    (when conn (d/release conn))
    (d/delete-database uri)
    (assoc component :conn nil)))

(def datomic-mock-db
  (map->Datomic-Test {:schema
                      [{:db/ident             :todo/title
                        :db/valueType         :db.type/string
                        :db/cardinality       :db.cardinality/one
                        :db/doc               "This is the title of the todo item"}
                       {:db/ident             :todo/completed?
                        :db/valueType         :db.type/boolean
                        :db/cardinality       :db.cardinality/one
                        :db/doc               "if true, this item is done"}]}))

(def uri "datomic:mem://localhost:4334/framework-test")
(def datomic-db (new-datomic-db uri))


(def datomic-dns-mock
  (map->Datomic-Test {:schema
                      [{:db/ident             :dns/name
                        :db/valueType         :db.type/keyword
                        :db/cardinality       :db.cardinality/one
                        :db/unique            :db.unique/identity
                        :db/doc               "Name of DNS"}
                       {:db/ident             :dns/path
                        :db/valueType         :db.type/keyword
                        :db/cardinality       :db.cardinality/one
                        :db/doc               "Name of DNS"}
                       {:db/ident             :dns/isactive?
                        :db/valueType         :db.type/boolean
                        :db/cardinality       :db.cardinality/one
                        :db/doc               "is server alive"}
                       {:db/ident             :dns/priority
                        :db/valueType         :db.type/long
                        :db/cardinality       :db.cardinality/one
                        :db/doc               "connection priority"}
                       {:db/ident             :dns/uri
                        :db/valueType         :db.type/string
                        :db/cardinality       :db.cardinality/one
                        :db/doc               "uri"}
                       {:db/ident             :dns/host
                        :db/valueType         :db.type/string
                        :db/cardinality       :db.cardinality/one
                        :db/doc               "server ip addres"}
                       {:db/ident             :dns/port
                        :db/valueType         :db.type/long
                        :db/cardinality       :db.cardinality/one
                        :db/doc               "server port"}
                       {:db/ident             :dns/serice-id-many
                        :db/isComponent       true
                        :db/valueType         :db.type/ref
                        :db/cardinality       :db.cardinality/many
                        :db/doc               "service id"}
                       {:db/ident             :dns/service-name
                        :db/valueType         :db.type/keyword
                        :db/cardinality       :db.cardinality/one
                        :db/unique            :db.unique/identity
                        :db/doc               "service name"
                        }
                       {:db/ident             :dns/service
                        :db/valueType         :db.type/ref
                        :db/cardinality       :db.cardinality/one
                        :db/doc               "service class"
                        }]}))

;; (defn database-many []
;;   (component/system-map
;;    :dev-hello4 (new-datomic-db  )
;;    :mem-todo datomic-mock-db))

;;(def states (database-many))


(deftest datomic-lifecycle
  (testing "Datomic lifecycle operations."
    (alter-var-root #'datomic-db component/start)
    (is (= (type (:conn datomic-db))
           datomic.peer.LocalConnection))
    (is (:conn datomic-db))
    (is @(d/transact (:conn datomic-db)
                     [{:db/ident             :todo/title
                       :db/valueType         :db.type/string
                       :db/cardinality       :db.cardinality/one
                       :db/doc               "This is the title of the todo item"}
                      {:db/ident             :todo/completed?
                       :db/valueType         :db.type/boolean
                       :db/cardinality       :db.cardinality/one
                       :db/doc               "if true, this item is done"}]))
    (is (= 0 (todo-count (d/db (:conn datomic-db)))))
    (is (= 1 (let [t @(d/transact (:conn datomic-db) (todo-new "hello world"))]
               (todo-count (d/db (:conn datomic-db))))))
    (is (d/delete-database uri))
    (is (= [[3] [1]])
        (d/q '[:find  ?heads
               :with ?name
               :in [[?name ?heads]]
               ]
             [["Cemerick" 3]
              ["Chimra" 1]
              ]))
    (alter-var-root #'datomic-db component/stop)))



(deftest datomic-lifecycle-mock
  (testing "Datomic lifecycle operations."
    (alter-var-root #'datomic-mock-db component/start)
    (is (= (type (:conn datomic-mock-db))
           datomic.peer.LocalConnection))
    (is (:conn datomic-mock-db))
    (is (= 0 (todo-count (d/db (:conn datomic-mock-db)))))
    (is (= 1 (let [t @(d/transact (:conn datomic-mock-db) (todo-new "hello world"))]
               (todo-count (d/db (:conn datomic-mock-db))))))
    (alter-var-root #'datomic-mock-db component/stop)))


(deftest datomic-dns-mock-test
  (testing "Datomic lifecycle operations."
    (alter-var-root #'datomic-dns-mock component/start)
    (is (= (type (:conn datomic-dns-mock))
           datomic.peer.LocalConnection))
    (is (:conn datomic-dns-mock))
    (is (let [t @(d/transact
                       (:conn datomic-dns-mock)
                       [{:dns/name :bootstrap
                         :dns/isactive? true
                         :dns/host "192.168.3.3"
                         :dns/path :home:xen1:devops15
                         :dns/serice-id-many [{:dns/service-name :datomic
                                               :dns/isactive? true
                                               :dns/uri "datomic:mem://localhost:4334/framework-test"}
                                              {:dns/service-name :http-kit
                                               :dns/isactive? true
                                               :dns/port 4342 }
                                              {:dns/service-name :figwheel
                                               :dns/isactive? true
                                               :dns/port 4342 }]}])]
          t))
    (is (= :bootstrap (:dns/name
                       (d/touch
                        (d/entity (d/db (:conn datomic-dns-mock))
                                  (ffirst (d/q '[:find ?e
                                                 :where
                                                 [?e :dns/name :bootstrap]
                                                 [?e :dns/serice-id-many ?c]
                                                 ]
                                               (d/db (:conn datomic-dns-mock)))))))))
    (alter-var-root #'datomic-dns-mock component/stop)))


;; (deftest datomic-lifecycle
;;   (testing "Datomic lifecycle operations."
;;     (alter-var-root #'states component/start)
;;     (is (= :text-node (:dom/tag
;;                        (first
;;                         (:dom/child
;;                          (first
;;                           (:dom/child
;;                            (->>
;;                             (d/q '[:find ?c
;;                                    :where
;;                                    [?e :component :navcomp4]
;;                                    [?e :dom/child ?c]
;;                                    [?c :dom/tag ]]
;;                                  (d/db (:conn (:dev-hello4 states))))
;;                             (ffirst)
;;                             (d/entity (:conn (:dev-hello4 states)))
;;                             (d/touch)))))))))
;;     (alter-var-root #'states component/stop)))

;;(run-tests)
