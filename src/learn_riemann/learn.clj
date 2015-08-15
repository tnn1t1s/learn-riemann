(ns learn-riemann.learn
      (:require
        [riemann.client :as r]
        [chime :refer [chime-at chime-ch]]
        [clj-time.core :as t]
        [clj-time.periodic :refer [periodic-seq]]
        [clojure.core.async :as a :refer [<! go-loop]]
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
@(r/query c "service = \"%\"")


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
(comment
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
)



(defn riemann-synth [client service host intervals fx]
  (let [
      chimes (chime-ch
               (take intervals (periodic-seq (t/now) (-> 1 t/seconds)))
               )]
  (a/<!! (go-loop [counter 1]
                  (when-let [msg (<! chimes)]
                    (riemann-send client {
                                     :service service 
                                     :state "ok"
                                     :host host
                                     :metric (fx counter)})
                    (recur (inc counter)))))))

; generate a sin stream
(riemann-synth c "agent" "a" 100 #(+ 1 (Math/sin %)))

; triangles with amplitude 1 and period 10
(riemann-synth c "agent" "b" 100 #(/ (mod % 10) 10))
; random events with probability, p, and decay, t

