(ns ring.openapi30.openapi3
  (:require [clojure.string :as str]
            [plumbing.core :as p]
            [ring.swagger.swagger2 :as swagger2]
            [ring.swagger.common :as common]
            [ring.swagger.json-schema :as rsjs]
            [schema.core :as s]
            [schema-tools.core :as stc]
            [ring.swagger.core :as rsc]
            [midje.sweet :refer :all]))

(defmulti ^:private extract-parameter (fn [in _ _] in))

(defmethod extract-parameter :body [_ model options]
  (if model
    (let [schema (rsc/peek-schema model)
          schema-json (rsjs/->swagger model options)]
      (vector
       {:in "body"
        :name (or (common/title schema) "")
        :description (or (:description (rsjs/json-schema-meta schema)) "")
        :required (not (rsjs/maybe? model))
        :schema schema-json}))))

(defmethod extract-parameter :default [in model options]
  (if model
    (for [[k v] (-> model common/value-of stc/schema-value rsc/strict-schema)
          :when (s/specific-key? k)
          :let [rk (s/explicit-schema-key k)
                json-schema (rsjs/->swagger v options)]
          :when json-schema]
      (merge
       {:in (name in)
        :name (rsjs/key-name rk)
        :description ""
        :required (or (= in :path) (s/required-key? k))}
       json-schema))))

(defn- default-response-description
  "uses option :default-response-description-fn to generate
   a default response description for status code"
  [status options]
  (if-let [generator (:default-response-description-fn options)]
    (generator status)
    ""))

(defn convert-parameters [parameters options]
  (into [] (mapcat (fn [[in model]]
                     (println "in" in "\n" "model" model "\n")
                     (extract-parameter in model (assoc options :in in)))
                   parameters)))

(defn convert-responses [responses options]
  (let [responses (p/for-map [[k v] responses
                              :let [{:keys [schema headers]} v]]
                             k (-> v
                                   (cond-> schema (update-in [:schema] rsjs/->swagger options))
                                   (cond-> headers (update-in [:headers] (fn [headers]
                                                                           (if headers
                                                                             (->> (for [[k v] headers]
                                                                                    [k (rsjs/->swagger v options)])
                                                                                  (into {}))))))
                                   (update-in [:description] #(or %
                                                                  (:description (rsjs/json-schema-meta v))
                                                                  (default-response-description k options)))
                                   common/remove-empty-keys))]
    (if-not (empty? responses)
      responses
      {:default {:description ""}})))

(defn convert-operation
  "Returns a map with methods as keys and the Operation
   maps with parameters and responses transformed to comply
   with Swagger spec as values"
  [operation options]
  (p/for-map [[k v] operation]
             k (-> v
                   (common/update-in-or-remove-key [:parameters] #(convert-parameters % options) empty?)
                   (update-in [:responses] convert-responses options))))

(defn swagger-path
  "Replaces Compojure/Clout style path params in uri with Swagger style
  path params.

  Does not support wildcard-paths or inline-regexes.

  The regex is copied from Clout."
  [uri]
  ;; TODO: In 1.0, leave it to client libs to build swagger style path template
  ;; Currently everyone needs to build Clout path is just extra step for all but
  ;; compojure-api.
  (str/replace uri #":([\p{L}_][\p{L}_0-9-]*)" "{$1}"))

(defn extract-paths-and-definitions [swagger options]
  (let [original-paths (or (:paths swagger) {})
        paths (reduce-kv
               (fn [acc k v]
                 (assoc acc
                   (swagger-path k)
                   (convert-operation v options)))
               (empty original-paths)
               original-paths)
        definitions (-> swagger
                        extract-models
                        (transform-models options))]
    [paths definitions]))

(defn ensure-body-sub-schemas [route]
  (if (get-in route [:parameters :re])
    (update-in route [:parameters :requestBodies :content ] #(rsc/with-named-sub-schemas % "Body"))
    route))

(defn process-contents [content]
  (for [[content-type schema] content]
    (rsc/with-named-sub-schemas schema "Body")))

(defn ensure-body-sub-schemas2 [route]
  (-> route
      :requestBody
      :content
      process-contents))

(defn ensure-response-sub-schemas [route]
  (if-let [responses (get-in route [:responses])]
    (let [schema-codes (reduce (fn [acc [k {:keys [schema]}]]
                                 (if schema (conj acc k) acc))
                               [] responses)
          transformed (reduce (fn [acc code]
                                (update-in acc [:responses code :schema] #(rsc/with-named-sub-schemas % "Response")))
                              route schema-codes)]
      transformed)
    route))

(defn transform-operations
  "Transforms the operations under the :paths of a ring-swagger spec by applying (f operation)
  to all operations. If the function returns nil, the given operation is removed."
  [f swagger]
  (let [initial-paths (:paths swagger)
        transformed (for [[path endpoints] initial-paths
                          [method endpoint] endpoints
                          :let [endpoint (f endpoint)]]
                      [[path method] endpoint])
        paths (reduce (fn [acc [kv endpoint]]
                        (if endpoint
                          (assoc-in acc kv endpoint)
                          acc)) (empty initial-paths) transformed)]
    (assoc-in swagger [:paths] paths)))

(defn ensure-body-and-response-schema-names
  "Takes a ring-swagger spec and returns a new version
   with a generated names for all anonymous nested schemas
   that come as body parameters or response models."
  [swagger]
  (->> swagger
       (transform-operations ensure-body-sub-schemas2)
       (transform-operations ensure-response-sub-schemas)))

(def openapi-defaults {:openapi "3.0.3"})

(s/defn openapi-json

  ([openapi :- (s/maybe ring.openapi30.full-schema/OpenApi)] (openapi-json openapi nil))
  ([openapi :- (s/maybe ring.openapi30.full-schema/OpenApi), options :- (s/maybe swagger2/Options)]
   (let [options (merge swagger2/option-defaults options)]
     (binding [rsjs/*ignore-missing-mappings* (true? (:ignore-missing-mappings? options))]
       (let [[paths definitions] (-> openapi
                                     ensure-body-and-response-schema-names
                                     (extract-paths-and-definitions options))]
         (common/deep-merge
          openapi-defaults
          (-> openapi
              (assoc :paths paths))))))))

