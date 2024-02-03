package com.zygne.chart.chart.model.chart;

import com.zygne.chart.chart.Canvas;

public class PriceGrid extends Object2d {


    @Override
    public void draw(Canvas canvas) {
        canvas.setColor("#ffffff");
        for(int i= 0; i < 30; i++){
            canvas.drawLine(x, i*100, width, i*100, Canvas.LineStyle.DASHED);
        }

    }
}