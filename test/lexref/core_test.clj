(ns lexref.core-test
  (:require [clojure.test :refer :all]
            [lexref.core :refer :all]
            [lexref.resource]))

(def ^:private number-release-count (atom 0))

(defmethod lexref.resource/release! java.lang.Number [this]
  (println "release" this)
  (swap! number-release-count inc))

(deftest test-with-lexref
  (testing "function application without intermediates"
    (let [n @number-release-count]
      (is (= (with-lexref (+ 1 2 3 4))
             10))
      (is (= @number-release-count n))))
  (testing "function application with intermediates"
    (let [n @number-release-count]
      (is (= (with-lexref (+ 1 (+ 2 (+ 3 4))))
             10))
      (is (= @number-release-count (+ n 2)))))
  (testing "higher order function application without intermediates"
    (let [n @number-release-count]
      (is (= (with-lexref (reduce + [1 2]))
             3))
      (is (= @number-release-count n))))
  (testing "higher order function application with intermediates"
    (let [n @number-release-count]
      (is (= (with-lexref (reduce + [1 2 3 4]))
             10))
      (is (= @number-release-count (+ n 2)))))
  (testing "map"
    (let [n @number-release-count]
      (is (= (with-lexref {:a (+ 1 (* 2 3))})
             {:a 7}))
      (is (= @number-release-count (inc n)))))
  (testing "update map"
    (let [n @number-release-count]
      (is (= (with-lexref (update {:a 1} :a + (* 2 3)))
             {:a 7}))
      (is (= @number-release-count (inc n)))))
  (testing "update map and conj vec"
    (let [n @number-release-count]
      (is (= (with-lexref (conj [] (update {:a 1} :a + (* 2 3))))
             [{:a 7}]))
      (is (= @number-release-count (inc n)))))
  (testing "update map  and conj vec 2"
    (let [n @number-release-count]
      (is (= (with-lexref (conj [] (update {:a (+ 1 2)} :a + (* 2 3))))
             [{:a 9}]))
      (is (= @number-release-count (+ n 2)))))
  (testing "reduce with complex function"
    (let [n @number-release-count]
      (is (= (with-lexref (reduce (fn [r [x w]] (+ r (* x w))) 1 [[2 3] [4 5] [6 7]]))
             69))
      (is (= @number-release-count (+ n 3 2))))) ; 3 for *, 2 for +
)
