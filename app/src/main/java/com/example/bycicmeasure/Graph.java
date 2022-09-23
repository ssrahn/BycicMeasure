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

    public Graph(GraphView graph) {
        this.graph = graph;
        this.series = new LineGraphSeries<>();
        this.graph.addSeries(this.series);
    }

    void resetDatapoints(ArrayList<Double> x, ArrayList<Double> y) {
        DataPoint[] dataPoints = new DataPoint[x.size()];
        for (int i=0; i<x.size(); i++) {
            dataPoints[i] = new DataPoint(x.get(i), y.get(i));
        }
        series.resetData(dataPoints);
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinX(Collections.min(x));
        graph.getViewport().setMaxX(Collections.max(x));
        graph.getViewport().setMinY(Collections.min(y));
        graph.getViewport().setMaxY(Collections.max(y));
    }

    void appendDatapoint(double y) {
        if (startTime == 0) {
            startTime = System.currentTimeMillis();
        }

        double x = (System.currentTimeMillis() - startTime) / 1000;
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(0);
        graph.getViewport().setMaxX(maxX);
        series.appendData(new DataPoint(x, y),x > maxX, 1000000);
    }
}
