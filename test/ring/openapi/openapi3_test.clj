(ns ring.openapi.openapi3-test
  (:require [clojure.test :refer :all])
  (:require [ring.openapi.openapi3 :refer :all]))

(def swagger-response {:paths {"/api" {:post  :body {:foo s/Str} :responses {200 {:description "ok" :schema Tag}}}}})