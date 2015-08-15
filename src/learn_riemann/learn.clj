(ns learn-riemann.learn
      (:require
        [riemann.client :as r]
        [chime :refer [chime-at chime-ch]]
        [clj-time.core :as t]
        [clj-time.periodic :refer [periodic-seq]]
        [incanter.core :as incanter]
        [clojure.core.async :as a :refer [<! go-loop]]
        [incanter.charts :as charts]
        )
      (:use [incanter core stats charts io]))


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


; testing riemann-7.config
@(r/query c "service = \"req rate\"")
(map #(riemann-send c {:service "http req rate" :state "ok" :metric (rand %)}) [1 2 3 4 5])
(riemann-send c {:service "0mq req rate" :state "ok" :metric (rand 100)})

; testing riemann-8.config
; this tests the 'project' stream. project takes multiple streams
; as argument and allows users to project them into a single stream
@(r/query c "service = \"mem percent\"")
@(r/query c "service = \"mem used\"")
@(r/query c "service = \"mem total\"")
(def x (chime-at
  (take 500 (periodic-seq (t/now) (-> 1 t/seconds)))
  (fn [time]
    (riemann-send c {:service "amem used" :state "ok" :metric (* 1.0 (rand 100))})
    (riemann-send c {:service "amem total" :state "ok" :metric 100.0}))))

;; testing coalesce
(def x (chime-at
  (take 100 (periodic-seq (t/now) (-> 1 t/seconds)))
  (fn [time]
    (riemann-send c {:service "disk xyz" :state "ok" :host "a" :metric 1.23})
    (riemann-send c {:service "disk xyz" :state "ok" :host "b" :metric 2.77}))))
;
(def x (chime-at
  (take 100 (periodic-seq (t/now) (-> 1 t/seconds)))
  (fn [time]
    (riemann-send c {:service "disk xyz" :state "ok" :host "a" :metric 1.23})
    (riemann-send c {:service "disk xyz" :state "ok" :host "b" :metric 2.77}))))

(def x (chime-at
  (take 100 (periodic-seq (t/now) (-> 1 t/seconds)))
  (fn [time]
    (riemann-send c {:service "agent heartbeat" :state "ok" :host "a" :metric 1.23})
    (riemann-send c {:service "agent heartbeat" :state "ok" :host "b" :metric 2.77}))))



(let [chimes (chime-ch [(-> 2 t/secs t/from-now)
                                                (-> 3 t/secs t/from-now)])]
    (a/<!! (go-loop []
                               (when-let [msg (<! chimes)]
                                              (prn "Chiming at:" msg)
                                                           (recur)))))

