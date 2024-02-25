(ns ring.openapi.json-schema-test
  (:require [midje.sweet :refer :all]
            [schema.core :as s]
            [plumbing.core :as p]
            [plumbing.fnk.pfnk :as pfnk]
            [ring.openapi.json-schema :as rsjs]
            [ring.swagger.core :as rsc]
            [ring.swagger.extension :as extension]
            [linked.core :as linked])
  (:import [java.util Date UUID Currency]
           [org.joda.time DateTime LocalDate LocalTime]
           [java.util.regex Pattern]
           [clojure.lang Symbol]
           (java.io File)))

(s/defschema Model {:value String})

(s/defrecord Keyboard [type :- (s/enum :left :right)])
(s/defrecord User [age :- s/Int, keyboard :- Keyboard])

;; Make currency return nil for testing purposes
(defmethod rsjs/convert-class java.util.Currency [_ _] nil)

(facts "type transformations"
       (facts "models"
              (rsjs/->swagger Model) => {:$ref "#/components/schemas/Model"}
              (rsjs/->swagger [Model]) => {:items {:$ref "#/components/schemas/Model"}, :type "array"}
              (rsjs/->swagger #{Model}) => {:items {:$ref "#/components/schemas/Model"}, :type "array" :uniqueItems true})

       (fact "schema predicates"
             (fact "s/enum"
                   (rsjs/->swagger (s/enum :kikka :kakka)) => {:type "string" :enum [:kikka :kakka]}
                   (rsjs/->swagger (s/enum 1 2 3)) => {:type "integer" :format "int64" :enum (seq #{1 2 3})})

             (fact "s/maybe"
                   (fact "uses wrapped value by default with x-nullable true"
                         (rsjs/->swagger (s/maybe Long)) => (assoc (rsjs/->swagger Long) :x-nullable true))
                   (fact "adds allowEmptyValue when for query and formData as defined by the spec"
                         (rsjs/->swagger (s/maybe Long) {:in :query}) => (assoc (rsjs/->swagger Long) :allowEmptyValue true)
                         (rsjs/->swagger (s/maybe Long) {:in :formData}) => (assoc (rsjs/->swagger Long) :allowEmptyValue true))
                   (fact "uses wrapped value by default with x-nullable true with body"
                         (rsjs/->swagger (s/maybe Long) {:in :body}) => (assoc (rsjs/->swagger Long) :x-nullable true))
                   (fact "uses wrapped value for other parameters"
                         (rsjs/->swagger (s/maybe Long) {:in :header}) => (rsjs/->swagger Long)
                         (rsjs/->swagger (s/maybe Long) {:in :path}) => (rsjs/->swagger Long)))

             (fact "s/defrecord"
                   (rsjs/->swagger User) => {:type "object",
                                             :title "User",
                                             :properties {:age {:type "integer", :format "int64"},
                                                          :keyboard {:type "object",
                                                                     :title "Keyboard",
                                                                     :properties {:type {:type "string", :enum [:right :left]}},
                                                                     :additionalProperties false,
                                                                     :required [:type]}},
                                             :additionalProperties false,
                                             :required [:age :keyboard]} )))
