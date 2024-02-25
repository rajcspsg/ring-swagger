(ns ring.openapi.openapi3-test
  (:require [clojure.test :refer :all]
            [schema.core :as s]
            [ring.swagger.swagger2 :as swagger2]
            [ring.openapi.validator :as validator]
            [ring.openapi.full-schema :as full-schema]
            [ring.openapi.openapi3 :as openapi]
            [midje.sweet :refer :all]))

(defn validate-swagger-json [swagger & [options]]
  (s/with-fn-validation
   (validator/validate (openapi/openapi-json swagger options))))

(defn validate [schema & [options]]
  (s/with-fn-validation
   (if-let [input-errors (s/check full-schema/OpenApi schema)]
     {:input-errors input-errors}
     (if-let [output-errors (validate-swagger-json schema options)]
       {:output-errors output-errors}))))

(fact "empty spec"
      (let [swagger {:openapi "3.0.3" :info {:version "1.0.11" :title "Petstore API"} :paths {}}]
        (validate swagger) => nil))

#_(fact "minimalistic spec"
        (let [swagger {:openapi "3.0.3" :info {:version "1.0.11" :title "Petstore API"} :paths {"/ping" {:get {}}}}]
          (validate swagger) => nil))

#_(fact "more complete spec"
        (validate a-complete-openapi) => nil)