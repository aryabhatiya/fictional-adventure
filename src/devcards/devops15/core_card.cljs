(ns devops15.core-card
  (:require-macros
   [devcards.core :as dc])
  (:require
   [devops15.first-card]
   [devops15.datascript.conn]
   [devops15.datascript.entry]
   [devops15.datascript.explode]
   [devops15.datascript.filter]
   [devops15.datascript.index]
   [devops15.datascript.listen]
   [devops15.datascript.pull_api]
   [devops15.todo-app]))


(dc/start-devcard-ui!)
