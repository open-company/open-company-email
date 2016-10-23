(ns oc.email.lib.sparkline
  (:require [clojure.java.io :as io]
            [amazonica.aws.s3 :as s3]
            [oc.email.config :as config])
  (:import [java.awt.geom Ellipse2D$Double]
           [org.jfree.data.category DefaultCategoryDataset]
           [org.jfree.chart ChartFactory JFreeChart ChartColor ChartUtilities]
           [org.jfree.chart.plot PlotOrientation]
           [org.jfree.chart.renderer.category LineAndShapeRenderer BarRenderer StandardBarPainter]
           [org.jfree.ui RectangleInsets]))

(def line-width-per-datum 15)
(def bar-width-per-datum 10)
(def height 30)
(def buffer-percenct 0.3)

(def s3-url-fragment "s3.amazonaws.com")

(def colors {:black ChartColor/BLACK
             :blue ChartColor/BLUE
             :red ChartColor/DARK_RED
             :green ChartColor/DARK_GREEN})
(def default-color :black)

(defn- chart-data [data]
  (let [series (DefaultCategoryDataset.)]
    (doseq [datum (map-indexed vector data)]
      (.addValue series (second datum) "data" (inc (first datum))))
    series))

(defn- sparkchart [chart-type data color]
  (let [series (chart-data data)
        line? (= chart-type :line)
        bar? (= chart-type :bar)
        chart (if line?
                (ChartFactory/createLineChart nil nil nil series PlotOrientation/VERTICAL false false false)
                (ChartFactory/createBarChart nil nil nil series PlotOrientation/VERTICAL false false false))
        plot (.getCategoryPlot chart)
        range-axis (.getRangeAxis plot)
        domain-axis (.getDomainAxis plot)
        renderer (if line? (LineAndShapeRenderer.) (BarRenderer.))
        circle (java.awt.geom.Ellipse2D$Double. -2.0, -2.0, 4.0, 4.0)
        nothing (RectangleInsets. 0.0 0.0 0.0 0.0)
        color (or (colors color) (:black colors))
        min-point (apply min data)
        max-point (apply max data)
        buffer (* buffer-percenct max-point)
        max-range (+ max-point buffer)
        min-range (- min-point buffer)]
    (.setBorderVisible chart false)
    (.setPadding chart nothing)
    (.setVisible range-axis false)
    (.setVisible domain-axis false)
    (.setInsets plot nothing)
    (.setOutlineVisible plot false)
    (.setBackgroundPaint plot nil)
    (.setDomainGridlinesVisible plot false)
    (.setRangeGridlinesVisible plot false)
    (.setDomainCrosshairVisible plot false)
    (.setRangeCrosshairVisible plot false)
    (.setRenderer plot renderer)
    (.setSeriesPaint renderer 0 color)
    (when line?
      (.setSeriesShape renderer 0 circle)
      (.setLowerBound range-axis min-range)
      (.setUpperBound range-axis max-range))
    (when bar?
        (.setShadowVisible renderer false)
        (.setBarPainter renderer (StandardBarPainter.)))
    chart))

(defn- s3-upload [output-stream file-name]
  (let [data (.toByteArray output-stream)]
    (s3/put-object config/aws-creds
      :bucket-name config/aws-s3-chart-bucket
      :key file-name
      :input-stream (java.io.ByteArrayInputStream. data)
      :metadata {:content-length (count data)
                 :content-type "image/png"}
      :access-control-list {:grant-permission ["AllUsers" "Read"]})))

(defn- sparkchart-file
  [chart-type data file-name color]
  {:pre [(#{:line :bar} chart-type)
         (sequential? data)
         (every? number? data)
         ((set (keys colors)) color)]}  
  (let [chart (sparkchart chart-type data color)
        width-per-datum (if (= :line chart-type) line-width-per-datum bar-width-per-datum)
        width (* width-per-datum (count data))]
    ;; Chart as file
    (ChartUtilities/saveChartAsPNG (io/as-file file-name) chart width height)
    file-name))

(defn- sparkchart-html
  [chart-type data color]
  {:pre [(#{:line :bar} chart-type)
       (sequential? data)
       (every? number? data)
       ((set (keys colors)) color)]}
  (let [chart (sparkchart chart-type data color)
        line? (= :line chart-type)
        width-per-datum (if line? line-width-per-datum bar-width-per-datum)
        width (* width-per-datum (count data))
        label (str (if line? "Line" "Bar") " chart")
        klass (str "sparkchart " (if line? "sparkline" "sparkbar"))
        file-name (str (java.util.UUID/randomUUID) ".png")
        bucket-name config/aws-s3-chart-bucket
        file-url (str "https://" bucket-name "." s3-url-fragment "/" file-name)
        bytes (java.io.ByteArrayOutputStream.)]
    ;; Chart as bytes
    (ChartUtilities/writeChartAsPNG bytes chart width height)
    ;; Async S3 U/L
    (future (s3-upload bytes file-name))
    ;; hiccup style HTML response
    [:img {:src file-url :alt label :class klass}]))

(defn sparkline-file
  ([data file-name] (sparkline-file data file-name default-color))
  ([data file-name color] (sparkchart-file :line data file-name color)))

(defn sparkbar-file
  ([data file-name] (sparkbar-file data file-name default-color))
  ([data file-name color] (sparkchart-file :bar data file-name color)))

(defn sparkline-html
  ([data] (sparkline-html data default-color))
  ([data color] (sparkchart-html :line data color)))

(defn sparkbar-html
  ([data] (sparkbar-html data default-color))
  ([data color] (sparkchart-html :bar data color)))

(comment
  
  ;; For REPL testing and development

  (require '[oc.email.lib.sparkline :as sl] :reload)

  (sl/sparkline-file [1.1 2.1 3.2 3.3] "line.png")
  (sl/sparkline-file [1.1 2.1 3.2 3.3 5.8 4.7] "line.png" :blue)
  (sl/sparkbar-file [1100 2100 3200] "bar.png")
  (sl/sparkbar-file [1100 2100 3200 3300 5800] "bar.png" :red)
  (sl/sparkline-html [1.1 2.1 3.2 3.3 5.8 4.7])
  (sl/sparkbar-html [1100 2100 3200 3300 5800] :red)

)