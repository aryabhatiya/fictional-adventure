(ns datascript.perf
  (:require
    [clojure.string :as str]
    #?(:clj clojure.java.shell))
  #?(:cljs (:require-macros datascript.perf)))

(def ^:const   enabled? true)
(def ^:dynamic debug?   false)

#?(:clj
  (defmacro context []
    (when (and (System/getenv "BENCH_PROJECT")
               (System/getenv "BENCH_BUILD"))
      { :project (System/getenv "BENCH_PROJECT")
        :build   (System/getenv "BENCH_BUILD") })))

(def ^:dynamic *context* (datascript.perf/context))

(def ^:dynamic *warmup-t* 500)
(def ^:dynamic *bench-t*  1000)
(def ^:dynamic *step*     10)
(def ^:dynamic *repeats*  5)

#?(:cljs (enable-console-print!))

;; helpers

(defn percentile [xs n]
  (->
    (sort xs)
    (nth (min (dec (count xs))
              (int (* n (count xs)))))))

(defn to-fixed [n places]
  #?(:cljs (.toFixed n places)
     :clj  (format (str "%." places "f") (double n))))

(defn round [n]
  (cond
    (> n 1)        (to-fixed n 1)
    (> n 0.1)      (to-fixed n 2)
    (> n 0.001)    (to-fixed n 2)
;;     (> n 0.000001) (to-fixed n 7)
    :else          n))

(defn pad [n l]
  (let [s (str n)]
    (if (<= (count s) l)
      (str (apply str (repeat (- l (count s)) " ")) s)
      s)))

(defn format-number [n]
  (pad (round n) 6))

(defn format-time [dt]
  (str "[ " (format-number dt) " ms ]"))

#?(:cljs (defn ^number now [] (js/performance.now))
   :clj  (defn ^Long   now [] (/ (System/nanoTime) 1000000.0)))

(defn inst []
#?(:cljs (js/Date.)
   :clj  (java.util.Date.)))


;; flame graph

(defrecord Frame [parent start end message children])

(def current-frame (volatile! nil))

(defn println-frame [frame depth]
  (let [msg (apply str
                   (apply str (repeat depth "  "))
                   (interpose " " (:message frame)))]
    (if (:start frame)
      (println (format-time (- (:end frame) (:start frame))) msg)
      (println "[           ]" msg))
    (doseq [ch (:children frame)]
      (println-frame ch (inc depth)))))

(defn start-frame [time?]
  (vreset! current-frame (->Frame @current-frame (when time? (now)) nil nil [])))

(defn end-frame [& msg]
  (let [f ^Frame @current-frame
        f (assoc f
            :end (now)
            :message msg)
        p (some-> (:parent f)
                  (update :children conj f))]
    (when (nil? p)
      (println-frame f 0))
    (vreset! current-frame p)))

#?(:clj
  (defmacro when-debug [& body]
    (when enabled?
      `(when debug?
         ~@body))))

#?(:clj
  (defmacro debug [& msgs]
    (when enabled?
      `(when debug?
         (start-frame false)
         (end-frame ~@msgs)))))

#?(:clj
  (defmacro measure [body & msgs]
    (if enabled?
      (let [sym   (gensym)
            msgs* (clojure.walk/postwalk #(if (= (symbol "%") %) sym %) msgs)]
       `(if debug?
          (let [_#   (start-frame true)
                ~sym ~body]
            (end-frame ~@msgs*)
            ~sym)
          ~body))
      body)))


(defn short-circuit-frames [& [msg]]
  (loop []
    (when-not (nil? @current-frame)
      (end-frame (or msg "↑↑↑ exception ↑↑↑"))
      (recur))))


#?(:clj
   (defmacro with-debug [& body]
    `(binding [debug? true]
       (try
         ~@body
         (finally
           (short-circuit-frames))))))

;; minibench

#?(:clj
  (defmacro dotime [duration & body]
   `(let [start-t# (now)
          end-t#   (+ ~duration start-t#)]
      (loop [iterations# *step*]
        (dotimes [_# *step*] ~@body)
        (let [now# (now)]
          (if (< now# end-t#)
            (recur (+ *step* iterations#))
            (double (/ (- now# start-t#) iterations#))))))))

#?(:clj
  (defmacro minibench [spec & body]
   `(let [_#     (dotime *warmup-t* ~@body)
          avg-t# (dotime *bench-t* ~@body)]
      (println (format-time avg-t#) ~spec "avg time")
      (with-debug
        ~@body))))

(defn left-pad [s len char]
  (let [slen (count s)]
    (if (>= slen len)
      s
      (str (str/join "" (repeat (- len slen) char)) s))))

(defn printcol [& args]
  (->> args
    (map #(left-pad (str %) 10 " "))
    (apply println)))

#?(:clj
  (defmacro bench [spec & body]
   `(let [_#       (when-not *context* (println (str "\n" ~spec)))
          _#       (dotime *warmup-t* ~@body)
          results# (into [] (for [_# (range *repeats*)]
                              (dotime *bench-t* ~@body)))
          min#     (reduce min results#)
          med#     (percentile results# 0.5)
          max#     (reduce max results#)]
      (printcol ~@(vals spec) "\t" med#)
      #_(if *context*
        (println "{ :context"   (pr-str *context*)
                 "\n  :spec   " (pr-str ~spec)
                 "\n  :env    " (pr-str (array-map :ts (inst)))
                 "\n  :results" (pr-str (array-map :median med# :min min# :max max# :raw results#)) "}")
        (println 
          "[ min:"   (format-number min#)
          "] [ med:" (format-number med#)
          "] [ max:" (format-number max#)
          "] ms")))))
