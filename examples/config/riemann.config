; -*- mode: clojure; -*-
; vim: filetype=clojure

(def email (mailer))


(logging/init {:file "riemann.log"})

; Listen on the local interface over TCP (5555), UDP (5555), and websockets
; (5556)
(let [host "127.0.0.1"]
  (tcp-server {:host host})
  (udp-server {:host host}))

; Expire old events from the index every 5 seconds.
(periodically-expire 5)

(let [index (index)]
  ; Inbound events will be passed to these streams:
  (streams
    (default :ttl 60
      ; Index all events immediately.
      index
    
      ; debug mode
      ;(where (service "dz0")
      ;      prn)

      ; Log expired events.
      (expired
        (fn [event] (info "expired" event))))

      ; Split and Index
      (where (not (expired? event))
      ;; over time windows of 3 seconds...
        (fixed-time-window 3
        ;; calculate the average value , emit an average (summary) event
          (combine folds/mean
	  ;; if there are no events in the window, we can get nil events
	    (where (not (nil? event))
	       #(info "DJP: average event" %)
	       ;; collect the summary event over the last 3 fixed-time-windows
	       (moving-event-window 3
	         ;;find the summary event with the minimum average metric
	         (combine folds/minimum
	           ;; see if it breached the threshold
		   (where (> metric 1.0)
		     #(info "[DJP: IT IS HAPPENING AGAIN]" %)
		     ;; send the event in an email
		     prn)))))))))


