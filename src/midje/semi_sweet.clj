;; -*- indent-tabs-mode: nil -*-

(ns midje.semi-sweet
  (:use clojure.test
        midje.fakes
        [midje.util debugging form-utils file-position]
        [midje.production-mode]
        [clojure.contrib.ns-utils :only [immigrate]]))
(immigrate 'midje.unprocessed)

(def => "=>")   ; So every namespace uses the same qualified name.

(defonce
  #^{:doc "True by default.  If set to false, Midje checks are not
     included into production code, whether compiled or loaded."}
  *include-midje-checks* true)

(defn- only-mocked* [names]
  (let [declarations (map (fn [name] 
                              `(defn ~name [& args#] 
                                 (throw (Error. (str "#'" '~name " has no implementation. It's used as a prerequisite in Midje tests.")))))
                          names)]
    `(do ~@declarations)))

(defmacro only-mocked 
  "Defines a list of names as functions that have no implementation yet. They will
   throw Errors if ever called."
  [& names] (only-mocked* names))

(defmacro unfinished
  "Defines a list of names as functions that have no implementation yet. They will
   throw Errors if ever called."
  [& names] (only-mocked* names))

(defmacro fake 
  "Creates an fake map that a particular call will be made. When it is made,
   the result is to be returned. Either form may contain bound variables. 
   Example: (let [a 5] (fake (f a) => a))"
  [call-form => result & overrides]
  (let [[var-sym & args] call-form & overrides]
    ;; The (vec args) keeps something like (...o...) from being evaluated as a
    ;; function call later on. Right approach would seem to be '~args. That causes
    ;; spurious failures. Debug someday.
    (make-fake-map var-sym
                          `{:arg-matchers (map midje.fakes/arg-matcher-maker ~(vec args))
                            :call-text-for-failures (str '~call-form)
                            :result-supplier (fn [] ~result)
                            :type :fake}
                          overrides))
)

(defmacro not-called
  "Creates an fake map that a function will not be called.
   Example: (not-called f))"
  [var-sym & overrides]
  (make-fake-map var-sym
                        `{:call-text-for-failures (str '~var-sym " was called.")
                          :result-supplier (fn [] nil)
                          :type :not-called}
                        overrides)
)

;; I want to use resolve() to compare calls to fake, rather than the string
;; value of the symbol, but for some reason when the tests run, *ns* is User,
;; rather than midje.semi_sweet_test. Since 'fake' is used only in the latter,
;; the tests fail.
;;
;; FURTHERMORE, I wanted to use set operations to check for fake and not-called,
;; but those fail for reasons I don't understand. Bah.
(defn- fakes-and-overrides [form]
  (let [fake? #(and (seq? %)
                    (or (= "fake" (name (first %)))
                        (= "not-called" (name (first %)))))]
    (separate-by fake? form)))
        

(defmacro call-being-tested [call-form expected-result overrides]
  "Creates a map that contains a function-ized version of the form being 
   tested, an expected result, and the file position to refer to in case of 
   failure. See 'expect'."
  `(merge
    {:function-under-test (fn [] ~call-form)
     :expected-result ~expected-result
     :expected-result-text-for-failures '~expected-result
     :file-position (user-file-position)}
    (hash-map-duplicates-ok ~@overrides)))


(defmacro expect 
  "Run the call form, check that all the mocks defined in the fakes 
   (probably with 'fake') have been satisfied, and check that the actual
   results are as expected. If the expected results are a function, it
   will be called with the actual result as its single argument.

   To strip tests from production code, set either clojure.test/*load-tests*
   or midje.semi-sweet/*check* to false."
  [call-form => expected-result & other-stuff]
  (when (user-desires-checking?)
    (let [ [fakes overrides] (fakes-and-overrides other-stuff)]
      `(let [call# (call-being-tested ~call-form ~expected-result ~overrides)]
         (expect* call# (vector ~@fakes))))))
