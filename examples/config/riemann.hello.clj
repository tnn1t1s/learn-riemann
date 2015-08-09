; vim: filetype=clojure

; This is a Riemann config file. It's a Clojure program that describes how
; to process Riemann events. 

; I'm going to take you on a tour of Riemann monitoring.
; When we're done, you will be able to write your own configs
;
; If you aren't familiar with Riemann or its events, see
;
; https://github.com/aphyr/riemann
;
; First, let's set up a log file. If you need to debug your
; configuration, you can always call (debug <anything>).
; is included.

(logging/init :file "riemann.log")

; If you haven't seen Clojure before, (fxn arg1 arg2 ...) is a function call.
; :file is a keyword, and  "riemann.log" is a regular double-quoted string.

; Riemann Servers accept events from the network.
; Start a TCP and UDP server by running the following two functions.

(tcp-server)
(udp-server)

; By default Riemann listens on port 5555 and every interface. You can change
; these by providing arguments to the server functions.

; (tcp-server :host "12.34.56.78" :port 5555)

;; Riemann Events
; Riemann Events are comprised of a host, service, a state, and a value. For example, a Riemann event could be something like, ('ramrs1.pit.twosigma.com','mesos/master/leader', 1), indicating that the server ramrs1.pit thinks it is the mesos leader. Events typically measure things, for example ('ramkv1.pit.twosigma.com', riak/put99', 8484) means the server thinks it's 99% latency is 8484 milliseconds.

;; The Riemann index.
; The index stores a moving windowed state for all Events. Riemann clients
; can search the index for various states with a basic query language.
; The Riemann Dash is an example of this.
; Riemann can expire events which have exceeded their TTL.
; Presently the only implementation of the index protocol is backed by an
; in memory nonblockinghashmap, but Aphyr has mentioned plans to build an HSQLDB
; backend. (intern project? riak? cassandra?)


; A little Clojure
; You'll need to know a little Clojure to work with riemann.config
; The first language primitive we'll hit is 'let'
; (let [name value name2 value2 ...] body) means "inside body, let these names
; refer to these values."
; It's basically the same effect as setting variables in a procedural language.
; def myFunction():
;     "print DP"
;     name = "DP"
;     print name
; (fn [x] (let [name "DP"] (prn name)))
; http://kartar.net/2015/04/just-enough-clojure-for-riemann/



(let [my-index (index)

  ; Let's define a stream that feeds events into the index. We'll
  ; call it index for short

  index (update-index my-index)]

  ; Now we need to *do* something with the events that flow in. The Riemann
  ; engine applies each incoming event to a series of streams. 'streams' is a 
  ; function that takes a variable number of arguments, each consisting of a
  ; a function that accepts a map of events
  (streams

    ;; hello riemann
    ; the 'prn' function prints its input to the console.
    ; Let's print every event that comes in:
    prn

    ;; Next, lets build a stream that only responds to "error" events
    ;; 'where' is a stream function that passes on events where expr is true
    ;; all of the streams are documented nicely here:
    ;; http://riemann.io/api/riemann.streams.html
    (where (state "error")
           ; The where stream ignores any event that doesn't match. Events that
           ; do match get passed on to its children.
           ; Let's log those error events to the log:
           (fn [event] (info event))
    ) 

    ; Where is powerful. You can match using equality:
    (where (host "ramrs1.pit.twosigma.com")
        (fn [event] (info event))
    )

    ; Regular expressions
    (where (description #"ramrs+.pit.twosigma.com"))

    ; The presence of a given tag
    (where (tagged "mutant"))

    ; Arbitrary functions on values
    (where (> (* metric 1000) 2.5))

    ; Which makes range queries easy
    (where (< 5 metric 10))

    ; Boolean operators
    (where (not (or (tagged "www")
                    (and (state "ok") (nil? metric)))))

    ; And arbitrary functions
    (defn global? [event] (nil? (:host event)))
    (where (global? event))
         
    ; ok, lets do something actually useful.   
    ; Imagine you wanted to know the time it takes for Cook to respond to 
    ; requests to the debug endpoint. The events look like:
    ;
    ; {:service "cook/rest/debug"
    ;  :metric: 0.240} ; 240 milliseconds
    ;
    ; So first, we select only the API requests
    
    (where (service "cook/rest/debug")

           ; Now, we'll calculate the 50th, 95th, and 99th percentile for all
           ; requests in each 5-second interval.

           (percentiles 5 [0.5 0.95 0.99]

                        ; Percentiles will emit events like 
                        ; {:service "cook/rest/debug 0.5" :metric 0.12}
                        ; We'll add them to the index, so they can show up
                        ; on our dashboard.

                        index)

           ; What else can we do with API requests? Let's figure out the total
           ; request rate. (rate interval & children) sums up metrics and 
           ; divides by time. 
           
           (rate 5)

           ; But this isn't quite right--these event metrics are *times*, so
           ; we're actually calculating the number of seconds spent by the API,
           ; each second. So we *set* the metric of every event to 1, *then*
           ; take the rate:

           (with :metric 1 (rate 5 index))

           ; (with) takes each event and calls (rate) with a *changed*
           ; copy--one where :metric is always 1. Then (rate) adds up all those
           ; 1's over five seconds, and sends that metric to the index.
           
           ; (with) has a counterpart, by the way: (default). It works exactly
           ; the same, but it only alters the event when the value is nil. Both
           ; with and default accept maps as well:

           (default {:state "ok" :ttl 60} index)
    )))

; Imagine your web server sends an event every time it hits an exception. Your
; app is pretty sizeable and maintained by several people, so you attach tags
; for various parts--the model, view, controller, etc.
;
; {:service "web server"
;  :state "exception"
;  :description "my stacktrace"
;  :tags ["view"]}
;
; Let's send an email to the right team whenever an exception like this is
; thrown. This example uses local sendmail:

(def email (mailer {:from "riemann@trioptimum.com"}))

; You can use any options for https://github.com/drewr/postal
;
; (mailer {:from "riemann@trioptimum.com"
;          :host "mx1.trioptimum.com"
;          :user "foo"
;          :pass "bar"})

(streams
  (where (and (service "web server")
              (state "exception"))
         (tagged "controller"
                 (email "5551234567@txt.att.net"))
         (tagged "view"
                 (email "delacroix@trioptimum.com" "bronson@trioptimum.com"))
         (tagged "model"
                 (email "staff@vonbraun.mil"))))

; The mailer can accept lists of events, too. To avoid getting slammed with
; too many emails we can use the rollup function--it will combine multiple
; events into a single message. To send at most 5 emails every hour:

(def tell-ops (rollup 5 3600 (email "ops@vonbraun.mil")))
(streams
  (where (state "critical") tell-ops))

; See that? We used def to define a new stream--plugging together primitives to
; solve a specific problem. You can reuse tell-ops all over your config. If you
; come up with a stream that lots of people could use, send me a pull request
; and we'll make it a part of the standard release.

; Rollup preserves all events, but sometimes you just want to drop excess
; events on the floor. Let's send an email for at most 5 state changes every
; day.
(by [:host :service]
    (changed :state
             (throttle 5 (* 3600 24)
                       (email "grumpy@ts.com"))))

; You can forward to other monitoring systems too. Let's connect to graphite:

(def graph (graphite {:host "be2.tx"}))

; And graph the rate of web requests on each server. To do that, we'll
; need to split up the stream into several rates, one per host.

(streams
  (where (service "web req")
         (by :host 
             (rate 1 graph))))

; The (by) stream creates a new rate every time it sees a new host. It forwards
; all events from web1 to one rate, all events from web2 to another, and so on.

; By also comes in handy when you want to use a single where expression to
; track many distinct things.

(where (service #"^riak (gets|puts)")
       (by [:host :service] index graph))

; Sometimes you'll want to combine the state of several services. For instance,
; imagine that every server reports its current CPU use. You want to show only
; the *maximum* cpu state on your dashboard.

(where (service "cpu")
       ; Coalesce tracks the most recent event received for any given host and
       ; service (as long as it hasn't exceeded its TTL). Every time it
       ; receives an event, it forwards a list of all those events. 
       (coalesce
         ; Then we bring those states together using the combine stream, and
         ; any function that takes a list of events.
         (combine folds/maximum
                  index)))

; You'll also find folds/minimum, mean, median, and sum. See
; http://aphyr.github.com/riemann/riemann.folds.html

; When you have *many* events, you can use multiple Riemann servers to scale
; out. You might, for instance, run one Riemann server per datacenter, and
; forward only state changes in each service to a master server for a birds-eye
; view.

(let [client (tcp-client :host "aggregator")]
  (by [:host :service]
      (changed :state 
               (forward client))))

; When services disappear or fail, their states in the index will get stale.
; Periodically, Riemann can scan the index and delete states that have exceeded
; their TTL. You'll receive events with the deleted statee's original :host,
; :service, and :state "expired".
;
; To expire old states every ten seconds:

(periodically-expire 10)

; You can select expired events with where, or the expired stream:

(streams
  (where (state "expired") prn)
  (expired prn))

; This way, Riemann can issue an alert for a service that failed to check in
; regularly. The default TTL is 60 seconds, but you can submit ttls with each
; event or assign them using (with) or (default).

(streams
  (default :ttl 10 index)
  
  (by [:host :service]
    (changed :state email "dj@palaitis.com")))

; Any service that fails to check in within every 10 seconds will be removed
; and an alert sent.

; Now you're ready to write your own streams. Check out the streams API:
; http://aphyr.github.com/riemann/riemann.streams.html. Feel free to email me
; as well: aphyr@aphyr.com. Issues and pull requests welcome on github:
; https://github.com/aphyr/riemann