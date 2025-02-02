;
; Copyright 2020-2021 AppsFlyer
;
; Licensed under the Apache License, Version 2.0 (the "License")
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;      http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.
;
;

(ns com.appsflyer.donkey.donkey-spec
  (:require [clojure.spec.alpha :as s]
            [clojure.string])
  (:import (com.codahale.metrics MetricRegistry)
           (io.vertx.core.impl.cpu CpuCoreSensor)))


;; ------- Predicates ------- ;;

(s/def ::int>=0 (s/and int? #(<= 0 %)))
(s/def ::strings (s/coll-of string?))
(s/def ::not-blank (s/and string? (comp not clojure.string/blank?)))

;; ------- Donkey Specification ------- ;;

(s/def ::metrics-prefix string?)
(s/def ::metric-registry #(instance? MetricRegistry %))
(s/def ::worker-threads #(s/int-in-range? 1 500 %))
(s/def ::event-loops #(s/int-in-range? 1 (* 2 (CpuCoreSensor/availableProcessors)) %))
(s/def ::debug boolean?)

(s/def ::donkey-config (s/keys :opt-un [::metrics-prefix
                                        ::metric-registry
                                        ::worker-threads
                                        ::event-loops
                                        ::debug]))

(s/def ::handler fn?)
(s/def ::handlers (s/coll-of ::handler))
(s/def ::method #{:get :post :put :delete :options :head :trace :connect :patch})
(s/def ::keep-alive boolean?)



;; ------- Middleware Specification ------- ;;

(s/def ::middleware ::handlers)


;; ------- Route Specification ------- ;;

(s/def ::path string?)
(s/def ::methods (s/coll-of ::method))
(s/def ::consumes ::strings)
(s/def ::produces ::strings)
(s/def ::handler-mode #{:blocking :non-blocking})
(s/def ::match-type #{:simple :regex})

(s/def :server/route (s/keys :req-un [::handler]
                             :opt-un [::path
                                      ::methods
                                      ::consumes
                                      ::produces
                                      ::handler-mode
                                      ::match-type
                                      ::middleware]))

;; ------- Resources Specification ------- ;;

(s/def ::resources-root ::not-blank)
(s/def ::index-page ::not-blank)
(s/def ::enable-caching boolean?)
(s/def ::max-age-seconds ::int>=0)
(s/def ::local-cache-duration-seconds pos-int?)
(s/def ::local-cache-size pos-int?)

(s/def :resources/route (s/keys :req-un [::path]
                                :opt-un [::produces]))
(s/def :resources/routes (s/coll-of :resources/route :distinct true :min-count 1))

(s/def ::resources (s/keys :req-un [:resources/routes]
                           :opt-un [::resources-root
                                    ::index-page
                                    ::enable-caching
                                    ::max-age-seconds
                                    ::local-cache-duration-seconds
                                    ::local-cache-size]))

;; ------- Server Specification ------- ;;

(s/def ::instances #(s/int-in-range? 1 500 %))
(s/def ::port #(s/int-in-range? 1 65536 %))
(s/def ::compression boolean?)
(s/def ::decompression boolean?)
(s/def ::host ::not-blank)
(s/def ::date-header boolean?)
(s/def ::content-type-header boolean?)
(s/def ::server-header boolean?)
(s/def ::tcp-no-delay boolean?)
(s/def ::tcp-quick-ack boolean?)
(s/def ::tcp-fast-open boolean?)
(s/def ::socket-linger-seconds ::int>=0)
(s/def ::accept-backlog pos-int?)
(s/def ::idle-timeout-seconds ::int>=0)
(s/def :server/routes (s/coll-of :server/route :distinct true :min-count 1))
(s/def ::error-handlers (s/map-of #(s/int-in-range? 400 600 %) fn?))

(s/def ::server-config (s/keys :req-un [::port (or :server/routes ::resources)]
                               :opt-un [::error-handlers
                                        ::instances
                                        ::middleware
                                        ::compression
                                        ::decompression
                                        ::host
                                        ::date-header
                                        ::content-type-header
                                        ::server-header
                                        ::tcp-no-delay
                                        ::tcp-quick-ack
                                        ::tcp-fast-open
                                        ::socket-linger-seconds
                                        ::accept-backlog
                                        ::keep-alive
                                        ::idle-timeout-seconds]))


;; ------- Client Specification ------- ;;

(s/def ::keep-alive-timeout-seconds pos-int?)
(s/def ::connect-timeout-seconds pos-int?)
(s/def ::follow-redirects boolean?)
(s/def ::max-redirects pos-int?)
(s/def ::default-port ::port)
(s/def ::default-host ::host)
(s/def ::user-agent string?)
(s/def ::enable-user-agent boolean?)
(s/def ::proxy-type #{:http :socks4 :socks5})
(s/def ::proxy-options (s/keys :req-un [::host ::port ::proxy-type]))
(s/def ::force-sni boolean?)
(s/def ::ssl boolean?)

(s/def ::client-config (s/keys :opt-un [::compression
                                        ::default-host
                                        ::default-port
                                        ::force-sni
                                        ::keep-alive
                                        ::keep-alive-timeout-seconds
                                        ::connect-timeout-seconds
                                        ::idle-timeout-seconds
                                        ::follow-redirects
                                        ::max-redirects
                                        ::user-agent
                                        ::enable-user-agent
                                        ::proxy-options
                                        ::ssl]))


;; ------- Client Request Specification ------- ;;


(s/def ::uri ::not-blank)
(s/def ::bearer-token ::not-blank)
(s/def ::basic-auth-options (s/map-of #{"id" "password"} ::not-blank))
(s/def ::query-params (s/every string? :kind map?))
(s/def ::headers (s/every string? :kind map?))

(s/def ::client-request (s/keys :req-un [::method]
                                :opt-un [::uri
                                         ::host
                                         ::port
                                         ::idle-timeout-seconds
                                         ::bearer-token
                                         ::basic-auth-options
                                         ::query-params
                                         ::headers]))
