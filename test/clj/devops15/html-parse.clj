(ns devops15.html-parse
  [pl.danieljanus.tagsoup :as html]
  [clojure.zip :as zip]
  [datomic.api :as d])

(defn- to-entity [node]
  (if-not (string? node)
    (merge
     {:dom/tag (html/tag node)}
     ;;include attributes, but not :id
     {:element (vec  (map (fn [[k v]]
                            {:element/name k
                             :element/str v}) (html/attributes node) ))}
     ;;id will ne given a special name
     (when-let [id (:id node)] {:dom/id id})
     ;;assoc children -- if present
     (when-let [children (html/children node)]
       {:dom/child (vec children)}))
    ;;if it's a string, encode as text-node
    {:dom/tag :text-node
     :dom/text    node}))
