(ns learn-riemann.learn
      (:require
        [riemann.client :as r]
        [chime :refer [chime-at]]
        [clj-time.core :as t]
        [clj-time.periodic :refer [periodic-seq]]
        [incanter.core :as incanter]
        [incanter.charts :as charts]
        ))


;; getting events into riemann
(defn riemann-send [c e]
  "given a riemann client and an event, send to riemann"
  (-> c (r/send-event e)
      (deref 5000 ::timeout)))

; initialize client connection on localhost
(def c (r/tcp-client {:host "127.0.0.1"}))

; chime-at and periodic-seq can be used to generate synthetic metrics
; this example generates uniform random metric from 0 to 100
(def x (chime-at
  (take 10 (periodic-seq (t/now) (-> 1 t/seconds)))
  (fn [time]
    (riemann-send c {:service "dz0" :state "ok" :metric (rand 100)}))))
; chime-at returns a zero-arg function that can be called
; to cancel the schedule.
; (x)
; query the index to see results
@(r/query c "service = \"dz0\"")



