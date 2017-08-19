(ns devops15.rum.cards
  (:require
   [clojure.string :as str]
   [rum.core :as rum]
   )
  (:require-macros
   [devcards.core :refer [defcard deftest]]))


(def *clock (atom 0))
(def *color (atom "#FA8D97"))
(def *speed (atom 167))
(def *bclock-renders (atom 0))

(defn tick []
  (reset! *clock (.getTime (js/Date.)))
  (js/setTimeout tick @*speed))

(defn format-time [ts]
  (-> ts
      (js/Date.)
      (.toISOString)
      (subs 11 23)))

(tick)

(defonce board-width 19)
(defonce board-height 10)

(defn prime? [i]
  (and (>= i 2)
       (empty? (filter #(= 0 (mod i %))
                       (range 2 i)))))

(defn initial-board []
  (->> (map prime? (range 0 (* board-width board-height)))
       (partition board-width)
       (mapv vec)))

(def *board (atom (initial-board)))
(def *board-renders (atom 0))

(defn periodic-refresh [period]
  {:did-mount
   (fn [state]
     (let [react-comp  (:rum/react-component state)
           interval (js/setInterval #(rum/request-render react-comp) period)]
       (assoc state ::inserval interval)))
   :will-unmount (fn [state]
                   (js/clearInterval (::inserval state)))})

(rum/defc watches-count < (periodic-refresh 1000) [ref]
  [:span (count (.-watches ref))])


(rum/defc board-stats < rum/reactive [*board *renders]
  [:div.starts {:style {:display :flex
                        :justify-content :space-around}}
   (map-indexed
    (fn [index [name val]]
      [:div {:key (str "board-stats-" index)
             :style {:display :flex
                     :flex-direction :column}}
       [:div {:style {:background-color "#EB7F00"
                      :border "1px solid #ddd"
                      :width "38px"
                      :height "38px"
                      :color "#ACF0F2"
                      :border-radius "50%"
                      :display :flex
                      :align-items :center
                      :justify-content :center}} val]
       [:div {:style {:font-family "'Open Sans', sans-serif"
                      :color "#225378"
                      }} name] ])
    [["Renders"
      (rum/react *renders)]
     ["Board"
      (watches-count *board)]
     ["Color"
      (watches-count *color)]])])

(rum/defc cell < rum/reactive [x y]
  (swap! *board-renders inc)
  (let [*cursor (rum/cursor-in *board [y x])]
    [:div {:style {:background-color (if (rum/react *cursor)
                                       (rum/react *color)
                                       "#ddd")
                   :margin "0 1px 1px 0"
                   :display :inline-block
                   }
           :on-mouse-over (fn [_]
                            (swap! *cursor not) nil)}
     ]))

(rum/defc board-reactive []
  [:div
   (board-stats *board *board-renders)
   [:div {:style {:display :grid
                  :grid-template-columns (str/join " " (repeat board-height "1fr"))
                  :grid-template-rows (str/join " " (repeat board-width "25px"))
                  }}
    (for [x (range 0 board-width)
          y (range 0 board-height)]
      (rum/with-key
        (cell x y)
        [x y]))]])

(rum/defc bit < rum/static [n bit]
  [:td.bclock-bit {:style {:backgroundColor
                           (if (bit-test n bit)  @*color "#ddd")
                           }}])

(rum/defc binary-clock < rum/reactive []
  (let [ts   (rum/react *clock)
        msec (mod ts 1000)
        sec  (mod (quot ts 1000) 60)
        min  (mod (quot ts 60000) 60)
        hour (mod (quot ts 3600000) 24)
        hh   (quot hour 10)
        hl   (mod  hour 10)
        mh   (quot min 10)
        ml   (mod  min 10)
        sh   (quot sec 10)
        sl   (mod  sec 10)
        msh  (quot msec 100)
        msm  (->   msec (quot 10) (mod 10))
        msl  (mod  msec 10)]
    [:div
     [:table.bclock
      [:tbody
       [:tr [:td]      (bit hl 3) [:th] [:td]      (bit ml 3) [:th] [:td]      (bit sl 3) [:th] (bit msh 3) (bit msm 3) (bit msl 3)]
       [:tr [:td]      (bit hl 2) [:th] (bit mh 2) (bit ml 2) [:th] (bit sh 2) (bit sl 2) [:th] (bit msh 2) (bit msm 2) (bit msl 2)]
       [:tr (bit hh 1) (bit hl 1) [:th] (bit mh 1) (bit ml 1) [:th] (bit sh 1) (bit sl 1) [:th] (bit msh 1) (bit msm 1) (bit msl 1)]
       [:tr (bit hh 0) (bit hl 0) [:th] (bit mh 0) (bit ml 0) [:th] (bit sh 0) (bit sl 0) [:th] (bit msh 0) (bit msm 0) (bit msl 0)]
       [:tr [:th hh]   [:th hl]   [:th] [:th mh]   [:th ml]   [:th] [:th sh]   [:th sl]   [:th] [:th msh]   [:th msm]   [:th msl]]
       ]]]))

(rum/defc analog-clock < rum/reactive []
  (let [ts   (rum/react *clock)
        sec  (mod (quot ts 1000) 60)
        min  (mod (quot ts 60000) 60)
        hour (+ (mod (quot ts 3600000) 12) 7)
        sec-rotate (str "rotate" "( " (* sec 6)     " 16 " " 17 "  " )" )
        min-rotate (str "rotate" "( " (* min 6 )     " 16 " " 17 "  " )" )
        hour-rotate (str "rotate" "( " (* hour 30)     " 16 " " 17 "  " )" )]
    [:svg { :viewBox "0 0 32 32"
           :style {
                   :height "120px"
                   :width "120px"
                   :background-color "#22A62B"
                   :animation "anima 30s ease-out infinite"
                   }}
      [:rect {:id "hour-hand"
              :x 15.385
              :y 10.291
              :width 1.227
              :fill "#aaa"
              :transform hour-rotate
              :height 7.626}]
      [:rect {:id "miute-hand"
              :fill "#ccc"
              :x 15.385
              :y 6.464
              :transform min-rotate
              :width 1.227
              :height 11.439}]
      [:rect {:id "second-hand"
              :x 15.385
              :y 7.46
              :transform sec-rotate
              :fill "#EF4767"
              :width 0.24
              :height 9.38}]
      [:path {:d "M15.879,2.531c-8.14,0-14.739,6.599-14.739,14.739c0,8.14,6.599,14.739,14.739,14.739s14.739-6.6,14.739-14.739
    C30.618,9.13,24.02,2.531,15.879,2.531z M15.879,30.852c-7.511,0-13.6-6.089-13.6-13.6c0-7.511,6.089-13.601,13.6-13.601    c7.511,0,13.6,6.089,13.6,13.601C29.479,24.763,23.391,30.852,15.879,30.852z"
              :fill "#FE4365"}
       ]
     ]))


(rum/defc helloworld < rum/reactive []
  [:div "helloworld Time " (format-time (rum/react *clock)) ])

(defcard board-reactive
  (board-reactive))

(defcard alalog-clock
  (analog-clock))

(defcard board-stats
  (board-stats *board *board-renders))



(defcard helloworld-clock
  (binary-clock ))
