(defproject metosin/ring-swagger "0.26.2"
  :description "Swagger Spec for Ring Apps"
  :url "https://github.com/metosin/ring-swagger"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :dependencies [[cheshire "5.12.0"]
                 [metosin/ring-http-response "0.9.3"]
                 [ring/ring-core "1.10.0"]
                 [metosin/schema-tools "0.13.1"]
                 [prismatic/schema "1.4.1"]
                 [prismatic/plumbing "0.6.0"]
                 [metosin/scjsv "0.6.2"]
                 [com.networknt/json-schema-validator "1.0.87"]
                 [clj-time "0.15.2"]
                 [org.tobereplaced/lettercase "1.0.0"]
                 [potemkin "0.4.7"]
                 [frankiesardo/linked "1.3.0"]]
  :profiles {:dev {:plugins [[lein-clojars "0.9.1"]
                             [lein-ring "0.12.6"]
                             [lein-midje "3.2.1"]
                             [funcool/codeina "0.5.0"]]
                   :dependencies [[org.clojure/clojure "1.11.1"]
                                  [midje "1.10.9" :exclusions [org.clojure/clojure
                                                               commons-codec]]
                                  [ring-mock "0.1.5"]
                                  [metosin/ring-swagger-ui "5.9.0"]
                                  [javax.servlet/javax.servlet-api "4.0.1"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.11 {:dependencies [[org.clojure/clojure "1.11.0"]]}}
  :codeina {:sources ["src"]
            :target "gh-pages/doc"
            :src-uri "http://github.com/metosin/ring-swagger/blob/master/"
            :src-uri-prefix "#L"}
  :deploy-repositories [["releases" :clojars]]
  :aliases {"all" ["with-profile" "dev:dev,1.7:dev,1.9"]
            "test-ancient" ["midje"]})
