(ns devops15.parse
  (:require [clojure.test :refer :all]
            [instaparse.core :as instaparse]))

(def as-and-bs
  (instaparse/parser
   "S = AB*
    AB = A B
    A  = 'a'+
    B  = 'b'+"))

(as-and-bs "aaaaaaaaaabbaaaaaaaaaaaaabb")


(def tovsparser
  (instaparse/parser
   "TERM = <'('> ADD  <')'>
           | <'('> SUB  <')'>
           | N
    ADD = TERM <'+'> TERM
    SUB = TERM <'-'> TERM
    N   = '0' | '1' | '2'| '3' | '4' | '5' | '6' | '7' | '8' | '9' "))

(deftest numaricparse
  (is (= 2
         (instaparse/transform
          {:N (fn [num] (clojure.edn/read-string num))
           :TERM identity
           :ADD +
           :SUB -}
          (tovsparser "(1+(3-2))")))))
