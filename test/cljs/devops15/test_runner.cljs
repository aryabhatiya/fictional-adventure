(ns devops15.test-runner
  (:require
   [doo.runner :refer-macros [doo-tests]]
   [devops15.core-test]
   [devops15.common-test]))

(enable-console-print!)

(doo-tests 'devops15.core-test
           'devops15.common-test)
