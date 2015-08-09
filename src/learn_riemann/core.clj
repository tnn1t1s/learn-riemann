(ns learn-riemann.core
      (:require [riemann.client :as r]))

(defn riemann-send [c e]
  "given a riemann client, send an event"
  (-> c (r/send-event e)
      (deref 5000 ::timeout)))

; getting events into riemann
(def c (r/tcp-client {:host "127.0.0.1"}))
(riemann-send c {:service "dz0" :state "ok" :metric 3.0})

; querying events
@(r/query c "service = \"dz0\"")

(def x [5.0, 5.0, 5.0, 4.0, 6.0, 5.0, 8.0, 9.0, 7.0, 7.0, 7.0, 7.0, 8.0, 6.0, 7.0, 5.0, 7.0, 6.0, 9.0, 3.0, 6.0])

(take 5 (repeatedly #(rand-int 2)))
(def y (take 5 (repeatedly #(int 1))))

(map #(do
        (riemann-send c {:service "dz0" :state "ok" :metric %})
        (Thread/sleep 1000)) y)


