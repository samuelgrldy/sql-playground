(ns sql-playground.core
  (:gen-class)
  (:require [clojure.java.jdbc :as jdbc]
            [honey.sql :as sql]
            [honey.sql.helpers :as h]
            [clj-uuid :as uuid]))

(defonce db
  {:classname "org.sqlite.JDBC"
   :dbtype "sqlite"
   :dbname "resources/database.db"})

(defn uuid []
  (str (uuid/v1)))

(def conn
  "Get connection to database"
  (jdbc/get-connection db))


(defn create-table
  [table-name table-spec]
  "Create table in database. table-spec is a vec of vecs, where inner vecs are [column-name column-type] pairs."
  (try (->> table-spec
            (jdbc/create-table-ddl table-name)
            (jdbc/db-do-commands db))
       (catch Exception e (println (.getMessage e)))))

(defn read-table
  [table-name]
  "Read all rows from :table-name."
  (let [query (-> (h/select [:*])
                  (h/from table-name))
        sql-command (sql/format query)]
    (jdbc/query db sql-command)))

(defn read-all-table ;;masih bermasalah: blm ngeluarin isi table
  "Read all tables from database."
  []
  (let [query (-> (h/select [:*])
                  (h/from :sqlite_master)
                  (h/where := :type "table"))
        sql-command (sql/format query)]
    (jdbc/query db sql-command)))

(defn add-sg-root
  "Add root skill group to database."
  []
  (let [query (-> (h/insert-into :skillGroup)
                  (h/values [{:sg_id (uuid)
                              :name "root"
                              :parent_id nil}])
                  sql/format)]
    (jdbc/execute! db query)))

(defn add-sg
  "Add child skill group to parent skill group."
  [parent_id child-name]
  (let [parents (->> (read-table :skillGroup)
                     (filter #(= (:sg_id %) parent_id)))
        query (-> (h/insert-into :skillGroup)
                  (h/values [{:sg_id (uuid)
                              :name child-name
                              :parent_id parent_id}]))
        sql-command (sql/format query)]
    (if (empty? parents)
      (println "yatim piatu bos, gaada parentnya.")
      (jdbc/execute! db sql-command))))


(defn add-skill
  "Add skill to skill group. if skill already exist, connect it to skill group."
  [sg-id skill-name]
  (let [skill-id (uuid)
        skills (read-table :skill)]
    (if (empty? (filter #(= (:name %) skill-name) skills))
      (let [insert-query (-> (h/insert-into :skill)
                             (h/values [{:skill_id skill-id
                                         :name skill-name}]))
            insert-sql-command (sql/format insert-query)
            relation-query (-> (h/insert-into :skill-sg-relation)
                               (h/values [{:_id (uuid)
                                           :sg_id sg-id
                                           :skill_id skill-id}]))
            insert-relation-sql-command (sql/format relation-query)]
        (jdbc/execute! db insert-sql-command)
        (jdbc/execute! db insert-relation-sql-command))
      (let [relation-query (-> (h/insert-into :skill-sg-relation)
                               (h/values [{:_id (uuid)
                                           :sg_id sg-id
                                           :skill_id (:skill_id
                                                      (first (filter #(= (:name %) skill-name) skills)))}]))]
        (jdbc/execute! db (sql/format relation-query))))))

(defn delete-skill
  "delete skill and all of its relation from database."
  [skill-id]
  (let [delete-query (-> (h/delete-from :skill)
                         (h/where := :skill_id skill-id))
        delete-command (sql/format delete-query)
        delete-relation-query (-> (h/delete-from :skill-sg-relation)
                                  (h/where := :skill_id skill-id))
        delete-relation-command (sql/format delete-relation-query)]
    (jdbc/execute! db delete-relation-command)
    (jdbc/execute! db delete-command)))

(defn skills-by-sgid
  "Get all skills in skill group based on sg id."
  [sg-id]
  (let [relation (read-table :skill-sg-relation)
        skills (read-table :skill)
        matched-rels (->> relation
                          (filter #(= (:sg_id %) sg-id)))
        result (->> matched-rels
                    (mapcat #(->> skills
                                  (filter
                                   (fn [skill] (= (:skill_id skill) (:skill_id %)))))))]
    (if (empty? result)
      (println "gaada bos skill di sg-nya")
      result)))

(defn all-skill-group
  "Get all skill group from database."
  []
  (read-table :skillGroup))

(defn find-sg
  "Find skill group based on sg id."
  [sg-id]
  (let [all-sg (read-table :skillGroup)]
    (->> all-sg
         (filter #(= (:sg_id %) sg-id)))))

(defn all-skill
  "Get all skill from database."
  []
  (read-table :skill))

(defn find-skill
  "Find skill based on skill id."
  [skill-id]
  (let [all-skill (read-table :skill)]
    (->> all-skill
         (filter #(= (:skill_id %) skill-id)))))

(defn disconnect-skill
  "Disconnect skill from a skill group."
  [sg-id skill-id]
  (let [delete-query (-> (h/delete-from :skill-sg-relation)
                         (h/where [:= :sg_id sg-id] [:= :skill_id skill-id]))
        delete-command (sql/format delete-query)]
    (jdbc/execute! db delete-command)))

(defn connect-skill
  "Connect skill to a skill group."
  [new-sg-id skill-id]
  (let [relation-query (-> (h/insert-into :skill-sg-relation)
                           (h/values [{:_id (uuid)
                                       :sg_id new-sg-id
                                       :skill_id skill-id}]))
        insert-relation-sql-command (sql/format relation-query)
        duplicate? (seq (->> (read-table :skill-sg-relation)
                             (filter #(and (= (:skill_id %) skill-id)
                                           (= (:sg_id %) new-sg-id)))))]
    (if duplicate?
      (println "skill udah ada di sg ini bos.")
      (jdbc/execute! db insert-relation-sql-command))))

(defn disconnect-reconnect-skill
  "Disconnect skill from a skill group and reconnect it to another skill group."
  [old-sg-id new-sg-id skill-id]
  (if (seq (->> (read-table :skill-sg-relation)
                (filter #(and (= (:skill_id %) skill-id)
                              (= (:sg_id %) new-sg-id)))))
    (println "gajadi disconnect-reconnect bos, soalnya skill udah ada di new sg.")
    (do
      (disconnect-skill old-sg-id skill-id)
      (connect-skill new-sg-id skill-id))))

(defn reparent-sg
  "Reparent skill group to another skill group."
  [sg-id new-parent-id]
  (let [query (-> (h/update :skillGroup)
                  (h/set {:parent_id new-parent-id})
                  (h/where := :sg_id sg-id)
                  sql/format)]
    (jdbc/execute! db query)))

(defn all-child-sg
  "Get all child skill group from parent skill group."
  [parent-id]
  (let [all-sg (read-table :skillGroup)]
    (->> all-sg
         (filter #(= (:parent_id %) parent-id)))))

(defn update-sg
  "Update skill group name."
  [sg-id new-name]
  (let [query (-> (h/update :skillGroup)
                  (h/set {:name new-name})
                  (h/where := :sg_id sg-id)
                  sql/format)]
    (jdbc/execute! db query)))

(defn update-skill
  "Update skill name."
  [skill-id new-name]
  (let [query (-> (h/update :skill)
                  (h/set {:name new-name})
                  (h/where := :skill_id skill-id)
                  sql/format)]
    (jdbc/execute! db query)))


(defn delete-sg
  "Delete skill group from database based on sg-id. Also delete all of its children and relations in that skill group."
  [sg-id]
  (let [children (->> (read-table :skillGroup)
                      (filter #(= (:parent_id %) sg-id)))]

    (letfn [(delete-children-and-rel [child]
              (let [child-id (:sg_id child)]
                (jdbc/execute! db (sql/format (-> (h/delete-from :skillGroup)
                                                  (h/where := :sg_id child-id))))
                (jdbc/execute! db (sql/format (-> (h/delete-from :skill-sg-relation)
                                                  (h/where := :sg_id child-id))))
                (doseq [child-child (->> (read-table :skillGroup)
                                         (filter #(= (:parent_id %) child-id)))]
                  (delete-children-and-rel child-child))))]
      (doseq [child children]
        (delete-children-and-rel child)))
    (jdbc/execute! db (sql/format (-> (h/delete-from :skillGroup)
                                      (h/where := :sg_id sg-id))))
    (jdbc/execute! db (sql/format (-> (h/delete-from :skill-sg-relation)
                                      (h/where := :sg_id sg-id))))))


(defn orphaned-skill
  "find skill that has no parent."
  []
  (let [all-skill (read-table :skill)
        relation (read-table :skill-sg-relation)
        orphan? (fn [skill]
                  (empty? (->> relation
                               (filter #(= (:skill_id %) (:skill_id skill))))))]
    (filter orphan? all-skill)))


(defn delete-all-from-table
  "Delete all rows from table. for dev purposes. use with caution?"
  [table-name]
  (let [sql-command (-> (h/delete-from table-name)
                        sql/format)]
    (jdbc/execute! db sql-command)))

(defn drop-table
  "Drop table from database. for dev purposes use with caution?"
  [table-name]
  (let [query (h/drop-table table-name)
        sql-command (sql/format query)]
    (jdbc/execute! db sql-command)))





;;update skill group (DONE)
;;update skill (DONE)

;;delete sg (DONE)
;;add skill to sg + its connection (DONE)
;;delete skill (DONE)
;;fungsi buat cari skill yg yatim (DONE)

;;RELATION TABLE
;;cari skill yg ada di suatu sg (DONE)
;;disconnect-connect skill from sg (DONE)
;;reparenting (DONE)

;;bonus: dari select * jadi list of maps

;;top level sg
;;create sg child (parent yg mane)
;;bikin skill dan connectin ke sg
;;cari skill yg ada di sg mane
;;query


