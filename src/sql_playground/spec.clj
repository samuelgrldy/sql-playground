(ns sql-playground.spec
  (:require [clojure.java.jdbc :as jdbc]
            [honey.sql :as sql]
            [honey.sql.helpers :as h]
            [clj-uuid :as uuid]))


(def skill-group-spec
  [[:sg_id :text]
   [:name :text] 
   [:parent_id :text] ;;for level purposes 
   ])

(def skill-spec
  "can be part of multiple skill groups"
  [[:skill_id :text]
   [:name :text]])

(def skill-sg-relations-spec
  [[:sg_id :text]
   [:skill_id :text]])
