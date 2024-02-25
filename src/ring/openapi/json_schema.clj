(ns ring.openapi.json-schema
  (:require [schema.core :as s]
            [schema.spec.core :as spec]
            [schema.spec.variant :as variant]
            [ring.swagger.common :as common]
            [ring.swagger.core :as rsc]
            [ring.swagger.extension :as extension]
            [ring.swagger.json-schema :refer [maybe? properties schema-object additional-properties json-schema-meta describe key-name not-supported! assoc-collection-format reference? predicate-name-to-class]]))


; TODO: remove this in favor of passing it as options
(def ^:dynamic *ignore-missing-mappings* false)

;;
;; Schema implementation which is used wrap stuff which doesn't support meta-data
;;

(defrecord FieldSchema [schema]
  schema.core.Schema
  (spec [_]
    (variant/variant-spec
     spec/+no-precondition+
     [{:schema schema}]))
  (explain [_] (s/explain schema)))

(defmulti convert-class (fn [c options] c))

(defprotocol JsonSchema
  (convert [this options]))

(defn get-schema-locations [schema-location]
  (case schema-location
    :body        "#/components/requestBodies/"
    :response    "#/components/responses/"
    "#/components/schemas/"))

(defn reference [e {:keys [schema-location]}]
  (if-let [schema-name (s/schema-name e)]
    {:$ref (str (get-schema-locations schema-location) schema-name)}
    (if (not *ignore-missing-mappings*)
      (not-supported! e))))

(defn merge-meta
  [m x {:keys [::no-meta :key-meta]}]
  (if (and (not no-meta) (not (reference? m)))
    (merge (json-schema-meta x)
           (if key-meta (common/remove-empty-keys (select-keys key-meta [:default])))
           m)
    m))

;; Classes
(defmethod convert-class java.lang.Integer       [_ _] {:type "integer" :format "int32"})
(defmethod convert-class java.lang.Long          [_ _] {:type "integer" :format "int64"})
(defmethod convert-class java.lang.Double        [_ _] {:type "number" :format "double"})
(defmethod convert-class java.lang.Number        [_ _] {:type "number" :format "double"})
(defmethod convert-class java.lang.String        [_ _] {:type "string"})
(defmethod convert-class java.lang.Boolean       [_ _] {:type "boolean"})
(defmethod convert-class clojure.lang.Keyword    [_ _] {:type "string"})
(defmethod convert-class clojure.lang.Symbol     [_ _] {:type "string"})
(defmethod convert-class java.util.UUID          [_ _] {:type "string" :format "uuid"})
(defmethod convert-class java.util.Date          [_ _] {:type "string" :format "date-time"})
(defmethod convert-class org.joda.time.DateTime  [_ _] {:type "string" :format "date-time"})
(defmethod convert-class org.joda.time.LocalDate [_ _] {:type "string" :format "date"})
(defmethod convert-class org.joda.time.LocalTime [_ _] {:type "string" :format "time"})
(defmethod convert-class java.util.regex.Pattern [_ _] {:type "string" :format "regex"})
(defmethod convert-class java.io.File            [_ _] {:type "file"})

(extension/java-time
 (defmethod convert-class java.time.Instant   [_ _] {:type "string" :format "date-time"})
 (defmethod convert-class java.time.LocalDate [_ _] {:type "string" :format "date"})
 (defmethod convert-class java.time.LocalTime [_ _] {:type "string" :format "time"}))

(defmethod convert-class :default [e _]
  (if-not *ignore-missing-mappings*
    (not-supported! e)))

;;
;; Convert the most common predicates by mapping fn to Class
;;

(defn ->swagger
  ([x]
   (->swagger x {}))
  ([x options]
   (-> x
       (convert options)
       (merge-meta x options))))

(defn- try->swagger [v k key-meta]
  (try (->swagger v {:key-meta key-meta})
       (catch Exception e
         (throw
          (IllegalArgumentException.
           (str "error converting to swagger schema [" k " "
                (try (s/explain v) (catch Exception _ v)) "]") e)))))


(defn- coll-schema [e options]
  (-> {:type "array"
       :items (->swagger (first e) (assoc options ::no-meta true))}
      (assoc-collection-format options)))

(extend-protocol JsonSchema

  Object
  (convert [e _]
    (not-supported! e))

  Class
  (convert [e options]
    (if-let [schema (common/record-schema e)]
      (schema-object schema)
      (convert-class e options)))

  nil
  (convert [_ _]
    nil)

  FieldSchema
  (convert [e _]
    (->swagger (:schema e)))

  schema.core.Predicate
  (convert [e _]
    (some-> e :pred-name predicate-name-to-class ->swagger))

  schema.core.EnumSchema
  (convert [e opts]
    (merge (->swagger (class (first (:vs e))) opts) {:enum (seq (:vs e))}))

  schema.core.Maybe
  (convert [e {:keys [in] :as opts}]
    (let [schema (->swagger (:schema e) opts)]
      (condp contains? in
        #{:query :formData} (assoc schema :allowEmptyValue true)
        #{nil :body} (assoc schema :x-nullable true)
        schema)))

  schema.core.Both
  (convert [e opts]
    (->swagger (first (:schemas e)) opts))

  schema.core.Either
  (convert [e opts]
    (->swagger (first (:schemas e)) opts))

  schema.core.Recursive
  (convert [e opts]
    (->swagger (:derefable e) opts))

  schema.core.EqSchema
  (convert [e opts]
    (merge (->swagger (class (:v e)) opts)
           {:enum [(:v e)]}))

  schema.core.NamedSchema
  (convert [e opts]
    (->swagger (:schema e) opts))

  schema.core.One
  (convert [e opts]
    (->swagger (:schema e) opts))

  schema.core.AnythingSchema
  (convert [_ {:keys [in] :as opts}]
    (if (and in (not= :body in))
      (->swagger (s/maybe s/Str) opts)
      {}))

  schema.core.ConditionalSchema
  (convert [e _]
    {:oneOf (vec (keep (comp ->swagger second) (:preds-and-schemas e)))})

  schema.core.CondPre
  (convert [e _]
    {:oneOf (mapv ->swagger (:schemas e))})

  schema.core.Constrained
  (convert [e _]
    (->swagger (:schema e)))

  java.util.regex.Pattern
  (convert [e _]
    {:type "string" :pattern (str e)})

  ;; Collections

  clojure.lang.Sequential
  (convert [e options]
    (coll-schema e options))

  clojure.lang.IPersistentSet
  (convert [e options]
    (assoc (coll-schema e options) :uniqueItems true))

  clojure.lang.IPersistentMap
  (convert [e {:keys [properties?] :as opts}]
    (if properties?
      {:properties (properties e)}
      (reference e opts)))

  clojure.lang.Var
  (convert [e opts]
    (reference e opts)))
