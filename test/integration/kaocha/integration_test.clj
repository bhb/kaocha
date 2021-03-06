(ns kaocha.integration-test
  (:require [kaocha.test-util]
            [clojure.test :refer :all]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [kaocha.config]
            [matcher-combinators.test]
            [matcher-combinators.matchers :as m]
            [kaocha.config :as config]))

(defn invoke-runner [& args]
  (apply shell/sh "clojure" "-A:dev" "-m" "kaocha.runner" "--no-color" "--no-randomize" args))

(defn invoke-with-config [config & args]
  (let [tmpfile (java.io.File/createTempFile "tests" ".edn")]
    (doto tmpfile
      (.deleteOnExit)
      (spit (str "#kaocha/v1" (prn-str config))))
    (apply shell/sh
           "clojure" "-A:dev" "-m" "kaocha.runner"
           "--config-file" (str tmpfile)
           "--no-randomize"
           args)))

(deftest command-line-runner-test
  (testing "it elegantly reports when no tests are found"
    (is (match? (invoke-with-config {:color? false
                                     :tests  [{:id          :empty
                                               :test-paths  ["fixtures/a-tests"]
                                               :ns-patterns ["^foo$"]}]})
                {:exit 0, :out "[]\n0 test vars, 0 assertions, 0 failures.\n", :err ""})))

  (testing "--fail-fast"
    ;; TODO on CI this produces FAIL in (fail-1) (NativeMethodAccessorImpl.java:-2)
    #_(is (match? {:err  ""
                   :out  (str "[(.)][(..F)]\n\n"
                              "FAIL in (fail-1) (hello_test.clj:12)\n"
                              "expected: false\n"
                              "  actual: false\n"
                              "3 test vars, 4 assertions, 1 failures.\n")
                   :exit 1}
                  (invoke-runner "--config-file" "fixtures/with_failing.edn" "--no-color" "--fail-fast"))))

  (testing "Invalid suite"
    (is (match? {:err  ""
                 :out  "No such suite: :foo, valid options: :a, :b.\n"
                 :exit 254}
                (invoke-runner "--config-file" "fixtures/tests.edn" "--no-color" "foo"))))

  (testing "Exception outside `is` with fail-fast"
    (let [result (invoke-runner "--config-file" "fixtures/with_exception.edn" "outside-is" "--fail-fast")]
      (is (match? {:exit 1 :err ""} result))
      (is (re-seq #"\[\(E\)\]

ERROR in \(exception-outside-is-test\) \(ddd/exception_outside_is.clj:4\)
Uncaught exception, not in assertion.
Exception: java.lang.Exception: booo" (:out result)))
      (is (re-seq #"1 test vars, 1 assertions, 1 errors, 0 failures\." (:out result))))))
