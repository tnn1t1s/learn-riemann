(ns learn-riemann.learn
      (:require
        [riemann.client :as r]
        [chime :refer [chime-at chime-ch]]
        [clj-time.core :as t]
        [clj-time.periodic :refer [periodic-seq]]
        [clojure.core.async :as a :refer [<! go-loop]]
        ))

; reference
; http://www.nuxeo.com/blog/monitoring-nuxeo/
; https://www.youtube.com/watch?v=czes-oa0yik&feature=youtu.be

; chapter
; measuring code w/ coda hale metrics and riemann
; observe, orient, decide, react
; what does it look like right now.
; how does that compare historically.
; do we need to do anything about it?
; ok. lets do it.



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
  (take 1 (periodic-seq (t/now) (-> 1 t/seconds)))
  (fn [time]
    (riemann-send c {:service "mem used" :state "ok" :metric (* 1.0 (rand 100))})
    (riemann-send c {:service "mem total" :state "ok" :metric 100.0}))))

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

; sin wave with period 
;(riemann-synth c "agent" "a" 100 #(+ 1 (* 0.5 (Math/sin %)))))

; sin wave with anomoly
; Now, lets take the perfect sin wave and introduce an anomoly.
; In the example below, we introduce a 0.1 probability of the 
; metric having an anomoly with a uniform distribution from 0 to 5
; Lets see what this looks like.
(comment
(riemann-synth c "agent" "a" 500 #(+ 1
                                     (if (< 0.1 (rand)) 0 (* 5 (rand)))
                                     (Math/sin (* 0.5 %))))
(riemann-synth c "agent" "b" 500 #(+ 1
                                     (if (< 0.05 (rand)) 0 (* 5 (rand)))
                                     (Math/sin (* 0.25 %))))
)
(riemann-synth c "threshold" "b" 50 #(+ 1
                                     (if (< 0.01 (rand)) 0 (* 5 (rand)))
                                     (Math/sin (* 0.25 %))))
(riemann-synth c "threshold" "c" 100 #(+ 0 (random-metric 1.0 (/ % %)))) 

; now that we have created some anomolies, lets set about detecting them.
; the first approach, and the most naive, is to set a threshold and 
; alert whenever the series crosses the threshold. 
; in this example, we know the series should be within the range 0 to 1.
; if the alert crosses this range, we should alert. In Riemann, we can 
; detect this easily. code example in riemann-10
; run this code and watch the dash, imagine getting pages everytime you
; see the alert turn critical.
; ideas; once it turns critical 5 times, leave it critical and then, only then, page.
; (that's actually kind of logical)
; http://riemann.io/howto.html#roll-up-and-throttle-events
; 
; this is sort of reasonable but falls apart when we try to use it in practice.
; while we may know and understand the behavior of a small subset of metrics,
; it is not practical to do this for all streams in our ecosystem.
; lets try something slightly more advanced. 
; statistic primer (mean, variance)
; threshold on the standard deviation
; http://dieter.plaetinck.be/post/practical-fault-detection-alerting-dont-need-to-be-data-scientist/
; advanced techniques
; MA crossing
; STDDEV crossings
; https://coderanger.net/talks/echo/

; really advanced
; FFT based techniques
; we can take the windowed FFT and detect changes in frequency comoposition in time
; or, set thresholds based on frequency range e.g. we don't expect high frequencies
; in this stream e.g. noise.


; now, if P(x), the probability of encountering an anomoly is high, we may see several
; anomolies in a fixed time window. we have a few ways to deal with this.
; 1) mean over fixed time window
; 2) flap detection e.g. stable
; and they have different purposes.

; testing : generate triangle waveshape with amplitude 1 and period 10
(riemann-synth c "agent" "b" 100 #(/ (mod % 10) 10))

; random events with probability, p, and decay, t
(defn random-metric [x range]
  "generate a positive metric with P(x) and value in <0,range>"
  (if (< x (rand)) 0 (* range (rand))))


