;; -*- indent-tabs-mode: nil -*-

(ns midje.util.t-exceptions
  (:use [midje.util.exceptions]
	[midje sweet test-util]
	[clojure.pprint]))

(def clojure-spewage-regexp #"^clojure\..*\(core.clj:\d+\)")

(fact "stacktraces can be fetched as strings"
  (stacktrace-as-strings (Throwable.)) => (contains clojure-spewage-regexp))

(fact "clojure spewage can be removed"
  (let [strings [ "clojure.something"
                  "java.something"
                  "midje.something"
                  "other.something"
                  "user$eval19.invoke(NO_SOURCE_FILE:1)"]]
    (relevant-strings strings) => ["other.something"]))

(fact "there is a format for printing exceptions"
  ;; since midje lines are omitted, there's not much we can check.
  (let [lines (friendly-exception-lines (Error. "message") ">>>")]
    (first lines) => #"Error.*message"
    (re-find #"^>>>" (first lines)) => falsey
    (rest lines) => empty?))
