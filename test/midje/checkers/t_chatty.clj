;; -*- indent-tabs-mode: nil -*-

(ns midje.checkers.t-chatty
  (:use midje.sweet
        [midje.checkers.chatty :only [chattily-false? tag-as-chatty-falsehood
                                      chatty-worth-reporting-on?
                                      chatty-checker-falsehood? chatty-untease
                                      chatty-checker?]]
        midje.test-util
        clojure.pprint))

(facts "about an extended notion of falsehood"
  (chattily-false? false) => truthy
  (chattily-false? true) => falsey
  (chattily-false? {:intermediate-results 3}) => falsey
  (chattily-false? (tag-as-chatty-falsehood {})) => truthy)

(facts "about chatty-checking utility functions"
  (tag-as-chatty-falsehood [5]) => chatty-checker-falsehood?

  (chatty-worth-reporting-on? 1) => falsey 
  (chatty-worth-reporting-on? '()) => falsey
  (chatty-worth-reporting-on? '(f)) => truthy
  (chatty-worth-reporting-on? ''(f)) => truthy
  (chatty-worth-reporting-on? '[f]) => falsey

  (chatty-untease 'g-101 '()) => [[] []]
  
  (chatty-untease 'g-101 '(1 (f) 33 (+ 1 2))) =>
                [ '( (f) (+ 1 2))  '(1 (g-101 0) 33 (g-101 1))  ])
  

;; The form of chatty checkers

(def actual-plus-one-equals-4 (chatty-checker [actual] (= (inc actual) 4)))
(def no-longer-limited-form (chatty-checker [actual] (= (inc actual) 4 (+ 2 actual))))

(facts "about the form of chatty-checkers"
  actual-plus-one-equals-4 => chatty-checker?
  no-longer-limited-form => chatty-checker?)

(facts "about what chatty-checkers return"
  (actual-plus-one-equals-4 3) => true
   
  (let [result (actual-plus-one-equals-4 4)]
    result => chatty-checker-falsehood?
    result => {:actual 4
              :intermediate-results [ ['(inc actual) 5] ] })

  (let [result (no-longer-limited-form 4)]
    result => chatty-checker-falsehood?
    result => {:actual 4
              :intermediate-results [ ['(inc actual) 5] ['(+ 2 actual) 6] ]}))



;; Chatty checkers: interaction with checkers that return chatty-failures.
(defn rows-in [rows] rows)
(defn has-rows [rows]
   (chatty-checker [actual-datastate]
                   ( (just (contains rows)) (rows-in actual-datastate))))

(after-silently 
 (fact (let [all (fn [name] [{:region 1, :name name}])]
         (all "PRK") => (has-rows [ {:region 1, :name "GDR"} ])))
 (fact @reported => (just (contains {:type :mock-expected-result-functional-failure
                                     :actual [{:region 1, :name "PRK"}]
                                     :intermediate-results '([(rows-in actual-datastate)
                                                              [{:region 1, :name "PRK"}]])}))))


;; Old bug. During midjcoexpansion/macroexpansion, the interior of a chatty checker
;; can turn into a lazy seq, which should be treated as if it were a list. This checks that. 
(after-silently 
 (fact
   (let [a-chatty-checker (fn [expected]
                            (chatty-checker [actual] (= expected (+ 1 actual))))]
     3 => (a-chatty-checker 33)))
 (fact @reported => (just (contains {:type :mock-expected-result-functional-failure
                                     :actual 3
                                     :intermediate-results '([(+ 1 actual) 4])}))))

