package com.example.bycicmeasure;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.ArrayList;
import java.util.Collections;

public class Graph {

    GraphView graph;
    LineGraphSeries<DataPoint> series;
    double maxX = 30;
    double startTime = 0;

    /**
     * Initialize a simple graph
     * @param graph
     */
    public Graph(GraphView graph) {
        this.graph = graph;
        this.series = new LineGraphSeries<>();
        this.graph.addSeries(this.series);
    }

    /**
     * Will set and overwrite the data-points and configure a static view for the graph
     *
     * @param x Array of x-Values
     * @param y Array of y-Values
     */
    void resetDatapoints(ArrayList<Double> x, ArrayList<Double> y) {
        // Create Datapoint Array for the graph
        DataPoint[] dataPoints = new DataPoint[x.size()];
        for (int i=0; i<x.size(); i++) {
            dataPoints[i] = new DataPoint(x.get(i), y.get(i));
        }
        // Reset the data in the series
        series.resetData(dataPoints);
        // Configure Viewport of Graph for best visibility
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinX(Collections.min(x));
        graph.getViewport().setMaxX(Collections.max(x));
        graph.getViewport().setMinY(Collections.min(y));
        graph.getViewport().setMaxY(Collections.max(y));
    }

    /**
     * Append data to the graph.
     * This will initialize a dynamic view of the graph
     *
     * @param y y-Value to append to the graph
     */
    void appendDatapoint(double y) {
        // Initialize x values with current time
        if (startTime == 0) {
            startTime = System.currentTimeMillis();
        }

        double x = (System.currentTimeMillis() - startTime) / 1000;
        // Configure ViewPort to show the last @maxX values
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(maxX);
        // Append the data
        series.appendData(new DataPoint(x, y),x > maxX, 1000000);
    }
}
