(ns ring.openapi30.openapi3
  (:require [ring.swagger.swagger2 :as swagger2]
            [ring.swagger.common :as common]
            [ring.swagger.json-schema :as rsjs]
            [schema.core :as s]
            [ring.swagger.core :as rsc]
            [midje.sweet :refer :all]))

(defn ensure-body-sub-schemas [route]
  (if (get-in route [:parameters :body])
    (update-in route [:parameters :body] #(rsc/with-named-sub-schemas % "Body"))
    route))

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
       (transform-operations ensure-body-sub-schemas)
       (transform-operations ensure-response-sub-schemas)))

(def openapi-defaults {:openapi "3.0.3"})

(s/defn openapi-json

  ([openapi :- (s/maybe ring.openapi30.full-schema/OpenApi)] (openapi-json openapi nil))
  ([openapi :- (s/maybe ring.openapi30.full-schema/OpenApi), options :- (s/maybe swagger2/Options)]
   (let [options (merge swagger2/option-defaults options)]
     (binding [rsjs/*ignore-missing-mappings* (true? (:ignore-missing-mappings? options))]
       (let [[paths definitions] (-> openapi
                                     ensure-body-and-response-schema-names
                                     (swagger2/extract-paths-and-definitions options))]
         (common/deep-merge
          openapi-defaults
          (-> openapi
              (assoc :paths paths))))))))

