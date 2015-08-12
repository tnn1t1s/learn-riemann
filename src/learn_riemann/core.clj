(ns learn-riemann.core
      (:require [riemann.client :as r]))

;; getting events into riemann
(defn riemann-send [c e]
  "given a riemann client and an event, send to riemann"
  (-> c (r/send-event e)
      (deref 5000 ::timeout)))

; initialize client connection on localhost
(def c (r/tcp-client {:host "127.0.0.1"}))

; send your first event
(riemann-send c {:service "dz0" :state "ok" :metric 90.0})
(riemann-send c {:service "dz0" :state "error" :metric 90.0})

; rieman-5
(riemann-send c {:service "/count/me" :state "ok" :metric 90.0})


; query the index for the event
@(r/query c "service = \"/xxx/yyy\"")
@(r/query c "service = \"dz0-mean\"")
@(r/query c "service =~ \"/count/me%\"")
@(r/query c "state = \"ok\"")


; testing the where stream
; error where stream
(riemann-send c {:service "dz0" :state "error" :metric 9000.0})
; host match where stream
(riemann-send c {:host "mymacbook.palaitis.com" :service "dz0" :state "ok" :metric 9000.0})
; match the boolean where stream
(riemann-send c {:service "service/dz" :state "ok" :tags ["dz0"] :metric 9000.0})
; this one will match two where streams
(riemann-send c {:service "service/dz" :state "error" :tags ["dz0"] :metric 9000.0})

; test fail?
(riemann-send c {:service "dz0" :state "error" :metric 9000.0})
(riemann-send c {:service "dz0" :state "error" :metric 10001.0})

; test call-rescue
(riemann-send c {:service "/mesoderm/dashboard/memory/ratio" :state "ok" :metric 0.94})
@(r/query c "service = \"/mesoderm/dashboard/memory/percent\"")


; send more events
(def x [5.0, 5.0, 5.0, 4.0, 6.0, 5.0, 8.0, 9.0, 7.0, 7.0, 7.0, 7.0, 8.0, 6.0, 7.0, 5.0, 7.0, 6.0, 9.0, 3.0, 6.0])
(def y (take 20 (repeatedly #(rand-int 10))))
(def z (take 5 (repeatedly #(int 1))))
; test expired events
(map #(do
        (riemann-send c {:service "dz0" :state "ok" :metric %})
        (Thread/sleep 1000)) z)


