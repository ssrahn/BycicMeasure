package com.example.bycicmeasure;

import android.content.Context;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.util.ArrayList;
import java.util.Collections;

public class Graph {

    GraphView graph;
    LineGraphSeries<DataPoint> series;

    public Graph(GraphView graph) {
        this.graph = graph;
        this.series = new LineGraphSeries<>();
        this.graph.getViewport().setXAxisBoundsManual(true);
        this.graph.getViewport().setYAxisBoundsManual(true);
        this.graph.addSeries(this.series);
    }

    void setDatapoints(ArrayList<Double> x, ArrayList<Double> y) {
        DataPoint[] dataPoints = new DataPoint[x.size()];
        for (int i=0; i<x.size(); i++) {
            dataPoints[i] = new DataPoint(x.get(i), y.get(i));
        }
        series.resetData(dataPoints);
        graph.getViewport().setMinX(Collections.min(x));
        graph.getViewport().setMaxX(Collections.max(x));
        graph.getViewport().setMinY(Collections.min(y));
        graph.getViewport().setMaxY(Collections.max(y));
    }
}
