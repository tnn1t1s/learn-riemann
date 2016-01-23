;(require '[riemann.elastic :as elastic])
; vim: filetype=clojure

(def email (mailer))
(logging/init {:file "riemann.log"})

; Listen on the local interface over TCP (5555), UDP (5555), and websockets
; (5556)
(let [host "127.0.0.1"]
  (tcp-server {:host host})
  (udp-server {:host host})
  (ws-server {:host host}))

; Expire old events from the index every 5 seconds.
(periodically-expire 5)


; Use (project) to pass a set of specific events to a stream
; (project) takes a vector of predicate expressions,
; like those used in (where). it maintains a vector
; of the most recent event for each predicate.a
; An incoming event is compared against each predicate; if it matches,
; the event replaces any previous event in that position and the
; entire vector of events is forwarded to all child streams.
(let [my-index (index)
      index (update-index my-index)]
  (streams
    ; In this example, we use (project) to create a new service,
    ; /count/sum, composed of the sum of /count/a and /count/b
    (project [(service "/count/a")
              (service "/count/b")]
             (smap folds/sum
                   (with :service "/count/sum"
                     prn
                     (fixed-time-window 3
                       (combine folds/maximum
                         (where (not (nil? event))
                                (with :service "/count/fail"
                                  (where (> metric 1.0)
                                         prn))))))))))



