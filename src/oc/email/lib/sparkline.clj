(ns oc.email.lib.sparkline
  (:require [clojure.java.io :as io])
  (:import [java.awt.geom Ellipse2D$Double]
           [org.jfree.data.category DefaultCategoryDataset]
           [org.jfree.chart ChartFactory JFreeChart ChartColor ChartUtilities]
           [org.jfree.chart.plot PlotOrientation]
           [org.jfree.chart.renderer.category LineAndShapeRenderer BarRenderer StandardBarPainter]
           [org.jfree.ui RectangleInsets]))

(def width-per-datum 20)
(def height 30)
(def buffer-percenct 0.3)

(def colors {:black ChartColor/BLACK
             :blue ChartColor/BLUE
             :red ChartColor/DARK_RED
             :green ChartColor/DARK_GREEN})

(defn- chart-data [data]
  (let [series (DefaultCategoryDataset.)]
    (doseq [datum (map-indexed vector data)]
      (.addValue series (second datum) "data" (inc (first datum))))
    series))

(defn- sparkchart [chart-type data color]
  {:pre [(#{:line :bar} chart-type)
         (sequential? data)
         (every? number? data)
         ((set (keys colors)) color)]}
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
        color (or (colors color) (:black colors))
        min-point (apply min data)
        max-point (apply max data)
        buffer (* buffer-percenct max-point)
        max-range (+ max-point buffer)
        min-range (- min-point buffer)
        nothing (RectangleInsets. 0.0 0.0 0.0 0.0)]
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

(defn sparkline
  ([data file-name] (sparkline data file-name :black))
  ([data file-name color]
  (let [chart (sparkchart :line data color)]
    (ChartUtilities/saveChartAsPNG (io/as-file file-name) chart (* width-per-datum (count data)) height))))

(defn sparkbar
  ([data file-name] (sparkline data file-name :black))
  ([data file-name color]
  (let [chart (sparkchart :bar data color)]
    (ChartUtilities/saveChartAsPNG (io/as-file file-name) chart (* width-per-datum (count data)) height))))

(comment
  
  ;; For REPL testing and development

  (require '[oc.email.lib.sparkline :as sl] :reload)

  (sl/sparkline [1.1 2.1 3.2 3.3] "line.png")
  (sl/sparkline [1.1 2.1 3.2 3.3 5.8 4.7] "line.png" :blue)
  (sl/sparkbar [1100 2100 3200] "bar.png" :green)
  (sl/sparkbar [1100 2100 3200 3300 5800] "bar.png" :red)

)
