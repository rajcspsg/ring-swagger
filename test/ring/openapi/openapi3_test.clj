(ns ring.openapi.openapi3-test
  (:require [clojure.test :refer :all]
            [ring.openapi.openapi3 :refer :all]
            [schema.core :as s]
            [ring.swagger.swagger2 :as swagger2]
            [ring.swagger.swagger2-full-schema :as full-schema]
            [ring.swagger.json-schema :as rsjs]
            [ring.swagger.extension :as extension]
            [ring.swagger.validator :as validator]
            [linked.core :as linked]
            [ring.util.http-status :as status]
            [midje.sweet :refer :all])
  (:import [java.util Date UUID]
           [java.util.regex Pattern]
           [org.joda.time DateTime LocalDate LocalTime]))
(s/defschema Tag
  {(s/optional-key :id) (rsjs/field s/Int {:description "Unique identifier for the tag"})
   (s/optional-key :name) (rsjs/field s/Str {:description "Friendly name for the tag"})})
(def swagger-response {:paths {"/api" {:post { :requestBody {:content {"application/json" {:foo s/Str}}} :responses {200 {:description "ok" :schema Tag}}}}}})

(deftest swagger-response-test
  (clojure.pprint/pprint (openapi-json swagger-response)))