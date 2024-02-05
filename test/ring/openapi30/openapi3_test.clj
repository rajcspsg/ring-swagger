(ns ring.openapi30.openapi3-test
  (:require [clojure.test :refer :all]
            [schema.core :as s]
            [ring.swagger.swagger2 :as swagger2]
            [ring.openapi30.validator :as validator]
            [ring.openapi30.full-schema :as openapi30]
            [midje.sweet :refer :all]))

(require 'ring.openapi30.openapi3 :reload)

(defn validate-swagger-json [swagger & [options]]
  (s/with-fn-validation
   (validator/validate (swagger2/swagger-json swagger options))))

(defn validate [schema & [options]]
  (s/with-fn-validation
   (if-let [input-errors (s/check openapi30/OpenApi schema)]
     {:input-errors input-errors}
     (if-let [output-errors (validate-swagger-json schema options)]
       {:output-errors output-errors}))))

(def a-complete-openapi
  {
   :openapi       "3.0.3"
   :info          {
                   :title          "Swagger Petstore - OpenAPI 3.0"
                   :description    "This is a sample Pet Store Server based on the OpenAPI 3.0 specification."
                   :termsOfService "http://swagger.io/terms/"
                   :contact        {
                                    :email "apiteam@swagger.io"
                                    }

                   :license        {
                                    :name "Apache 2.0"
                                    :url  "http://www.apache.org/licenses/LICENSE-2.0.html"
                                    }

                   :version        "1.0.11"

                   }
   :externalDocs  {
                   :description "Find out more about Swagger"
                   :url         "http://swagger.io"
                   }
   :servers       [{
                    :url "https://petstore3.swagger.io/api/v3"
                    }]

   :tags          [
                   {
                    :name         "pet"
                    :description  "everything about pets"
                    :externalDocs {
                                   :description "Find out more",
                                   :url         "http://swagger.io"
                                   }
                    }
                   ]
   :paths         {
                   "/pet" {
                           :put {
                                 :tags        ["pet"]
                                 :summary     "Update an existing pet"
                                 :description "Update an existing pet by Id"
                                 :operationId "updatePet"
                                 :requestBody {
                                               :description "Update an existent pet in the store"
                                               :content     {
                                                             "application/json" {
                                                                                 :schema {
                                                                                          :$ref "#/components/schemas/Pet"
                                                                                          }
                                                                                 }
                                                             }
                                               }
                                 :responses   {
                                               "200" {
                                                      :description "Successful operation"
                                                      :content     {
                                                                    "application/json" {
                                                                                        :schema {
                                                                                                 :$ref "#/components/schemas/Pet"
                                                                                                 }
                                                                                        }
                                                                    }
                                                      }
                                               "400" {
                                                      :description "Invalid ID supplied"
                                                      }
                                               "404" {
                                                      :description "Pet not found"
                                                      }
                                               "405" {
                                                      :description "Validation exception"
                                                      }
                                               }
                                 }

                           }
                   }
   :components    {
                   :schemas {
                             "Category"    {
                                            :type       "object"
                                            :properties {
                                                         :id   {
                                                                :type    "integer"
                                                                :format  "int64"
                                                                :example 1
                                                                }
                                                         :name {
                                                                :type    "string"
                                                                :example "Dogs"
                                                                }
                                                         }
                                            }
                             "Tag"         {
                                            :type       "object"
                                            :properties {
                                                         :id   {
                                                                :type    "integer"
                                                                :format  "int64"
                                                                :example 1
                                                                }
                                                         :name {
                                                                :type "string"
                                                                }
                                                         }
                                            }

                             "Pet"         {
                                            :required   [:name :photoUrls]
                                            :type       "object"
                                            :properties {
                                                         :id        {
                                                                     :type    "integer"
                                                                     :format  "int64"
                                                                     :example 10
                                                                     }
                                                         :name      {
                                                                     :type    "string"
                                                                     :example "doggie"
                                                                     }
                                                         :category  {
                                                                     "$ref" "#/components/schemas/Category"
                                                                     }
                                                         :photoUrls {
                                                                     :type  "array"
                                                                     :xml   {
                                                                             :wrapped true
                                                                             }
                                                                     :items {
                                                                             :type "string"
                                                                             :xml  {
                                                                                    :name "photoUrl"
                                                                                    }
                                                                             }
                                                                     }
                                                         :status    {
                                                                     :type        "string"
                                                                     :description "pet status in the store"
                                                                     :enum        ["available" "pending" "sold"]
                                                                     }
                                                         }
                                            }
                             "ApiResponse" {
                                            :type       "object"
                                            :properties {
                                                         :code    {
                                                                   :type   "integer"
                                                                   :format "int32"
                                                                   }
                                                         :type    {
                                                                   :type "string"
                                                                   }
                                                         :message {
                                                                   :type "string"
                                                                   }
                                                         }
                                            :xml        {
                                                         :name "##default"
                                                         }
                                            }
                             }
                   :requestBodies {
                                   :Pet {
                                         :description "Pet object that needed to be added to the store"
                                         :content     {
                                                       "application/json" {
                                                                           :schema {
                                                                                    :$ref "#/components/schemas/Pet"
                                                                                    }
                                                                           }
                                                       }
                                         }
                                   }
                   }
   })


(fact "empty spec"
      (let [swagger {:openapi "3.0.3" :info {:version "1.0.11" :title "Petstore API"} :paths {}}]
        (validate swagger) => nil))

#_(fact "minimalistic spec"
        (let [swagger {:openapi "3.0.3" :info {:version "1.0.11" :title "Petstore API"} :paths {"/ping" {:get {}}}}]
          (validate swagger) => nil))

#_(fact "more complete spec"
        (validate a-complete-openapi) => nil)