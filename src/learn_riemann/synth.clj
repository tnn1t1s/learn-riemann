(ns learn-riemann.synth
      (:require
        [riemann.client :as r]
        [chime :refer [chime-at chime-ch]]
        [clj-time.core :as t]
        [clj-time.periodic :refer [periodic-seq]]
        [clojure.core.async :as a :refer [<! go-loop]]
        ))

;; Riemann Synth is a utility for generating signals to test anomoly detection
;; algorithms in Riemann. It is modeled after an old school analog synthesizer.
;; The signals it generates can be stochastic or deterministic, allowing for
;; many different types of testing and hopefully, improving the rigour of
;; anomoly detection algo development

;; Another great use of Riemann Synth is painting cool pictures in Riemann Dash.

;;;; Functions for getting events into Riemann
(defn riemann-send [c e]
  "given a riemann client and an event, send to riemann"
  (-> c (r/send-event e)
      (deref 5000 ::timeout)))


;;;; Riemann Synth

(defn riemann-synth [client service host intervals fx]
  "Riemann Synth generates signals for testing Riemann"
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

;;; Riemann Synth Helpers

;; random-metric
(defn random-metric [x range]
  "generate a positive metric with P(x) and value in <0,range>"
  (if (< x (rand)) 0 (* range (rand))))

;;; Examples

(comment
; initialize client connection on localhost
(def c (r/tcp-client {:host "127.0.0.1"}))
;; First, use Riemann Synth to generate a sin wave 
(riemann-synth c "/synth/sin" "myhost.com" 100 #(+ 1 (* 0.5 (Math/sin %))))

;; Next, use Riemann Synth to introduce anomolies into a signal
;; Lets take the sin wave and introduce an anomoly.
;; In the example below, we introduce a 0.1 probability of the 
;; metric having an anomoly where the size of the anomoly is 
;; uniformly distributed from 0 to 5
(riemann-synth c "/synth/sin/a" "myhost.com" 500 #(+ 1
                                     (if (< 0.1 (rand)) 0 (* 5 (rand)))
                                     (Math/sin (* 0.5 %))))
(riemann-synth c "/synth/sin/b" "myhost.com" 500 #(+ 1
                                     (if (< 0.05 (rand)) 0 (* 5 (rand)))
                                     (Math/sin (* 0.25 %))))

;; Here's another example of the same, this time, 
;; using the random-metric function to disturb the peace.
(riemann-synth c "threshold" "b" 50 #(+ 1
                                     (if (< 0.01 (rand)) 0 (* 5 (rand)))
                                     (Math/sin (* 0.25 %))))
(riemann-synth c "threshold" "c" 100 #(+ 0 (random-metric 1.0 (/ % %)))) 

;; Finally, here's a simple triangle waveshape
(riemann-synth c "agent" "b" 100 #(/ (mod % 10) 10))

;; I've played around with other signals, noise and their ratios. 
;; In the riemann-config examples, I try to detect the anomolies
;; with Riemann.
)

