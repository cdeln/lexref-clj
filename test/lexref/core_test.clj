(ns lexref.core-test
  (:require [clojure.test :refer :all]
            [lexref.core :refer :all]))

(deftest test-leaf-map
  (testing "leaf-map"
    (is (= (leaf-map vector '(1 [2 {:a :b :c #{3 4}}]))
           '([1] [[2] {:a [:b] :c #{[3] [4]}}])))))

(deftest test-leaf-collect
  (testing "leaf-collect"
    (is (= (leaf-collect '(1 [2 {:a :b :c #{3 4}}]))
           '(1 2 :b 3 4)))))
