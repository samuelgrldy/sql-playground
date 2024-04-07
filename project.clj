(defproject sql-playground "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [com.github.seancorfield/honeysql "2.6.1126"]
                 [sqlitejdbc "0.5.6"]
                 [org.clojure/java.jdbc "0.7.12"]
                 [danlentz/clj-uuid "0.1.9"]]
  :main ^:skip-aot sql-playground.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
