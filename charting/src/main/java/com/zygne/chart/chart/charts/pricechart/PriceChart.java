package com.zygne.chart.chart.charts.pricechart;

import com.zygne.chart.chart.Chart;
import com.zygne.chart.chart.RendererImpl;
import com.zygne.chart.chart.menu.*;
import com.zygne.chart.chart.menu.indicators.*;
import com.zygne.chart.chart.menu.indicators.creators.*;
import com.zygne.chart.chart.model.chart.*;
import com.zygne.chart.chart.Canvas;
import com.zygne.chart.chart.model.data.Quote;
import com.zygne.chart.chart.model.data.Serie;
import com.zygne.chart.chart.util.ZoomHelper;

import javax.swing.*;
import javax.swing.event.MouseInputAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class PriceChart extends MouseInputAdapter implements Chart,
        OptionsMenu.Listener,
        CandleSticksCreator.Callback,
        VolumeProfileCreator.Callback,
        VolumeIndicatorCreator.Callback,
        TimeCreator.Callback {

    private static final int DEFAULT_HEIGHT = 640;
    private static final int DEFAULT_WIDTH = 720;
    private final int labelWidth = 60;
    private double scale = 1;
    private int barWidth = 1;
    private int barSeparator = 0;
    private int lastBar = 0;
    private boolean scaling = false;
    private boolean stretching = false;

    int zoom = 6;

    private int loadY;

    private final List<Quote> bars = new ArrayList<>();
    private final List<Quote> volumeProfile = new ArrayList<>();

    private int canvasHeight = DEFAULT_HEIGHT;
    private int canvasWidth = DEFAULT_WIDTH;
    private final Camera camera;
    private int startY;
    private int startX;
    private String waterMarkText = "";
    private String titleText = "";
    private TextObject waterMark;
    private TextObject copyright;
    private TopBar topBar;
    private final List<Object2d> objects = new ArrayList<>();
    private final RendererImpl renderer;
    private final int percentile = 90;

    private CandleSticksIndicator candleSticksIndicator = null;
    private VolumeProfileIndicator volumeProfileIndicator = null;
    private VolumeIndicator volumeIndicator = null;
    private TimeIndicator timeIndicator = null;

    private final PriceScale priceScale = new PriceScale();

    public PriceChart() {
        this.camera = new Camera(0, 0);
        this.camera.setX(0);
        this.camera.setY(0);
        this.camera.setHeight(canvasHeight);
        this.camera.setWidth(canvasWidth);

        renderer = new RendererImpl(camera);

        setUp();
    }

    private void setUp() {
        waterMark = new TextObject(0, 0, canvasWidth, canvasHeight);
        waterMark.setFontSize(TextObject.FontSize.LARGE);
        waterMark.setText(waterMarkText);
        waterMark.setzOrder(-1);
        this.waterMark.setColor("#00306C");

        copyright = new TextObject(0, 0, canvasWidth, 50);
        copyright.setFontSize(TextObject.FontSize.MEDIUM);
        copyright.setText("Zygne Chart");
        copyright.setzOrder(-1);
        this.copyright.setColor("#00306C");

        StatusBar statusBar = new StatusBar();
        statusBar.setWidth(canvasWidth);
        statusBar.setHeight(canvasHeight);
        statusBar.setLabelWidth(labelWidth);
        statusBar.setzOrder(2);

        priceScale.setX(canvasWidth - 50);
        priceScale.setY(1);
        priceScale.setWidth(100);
        priceScale.setHeight(camera.getHeight());
        priceScale.setzOrder(1);
        priceScale.setScale(scale);

        topBar = new TopBar();
        topBar.setX(0);
        topBar.setY(0);
        topBar.setWidth(canvasWidth);
        topBar.setHeight(canvasHeight);
        topBar.setzOrder(2);
        topBar.init();
        topBar.setLabelText(titleText + " " + percentile + "%");
    }


    @Override
    public void setSeries(List<List<Serie>> series) {
        reset();
        List<Serie> bars = series.get(0);

        List<Quote> quotes = new ArrayList<>();

        for(Serie s : bars){
            quotes.add((Quote) s);
        }

        double high = quotes.get(0).getHigh();

        if (high > 1000) {
            this.zoom = 4;
        } else if (high > 100) {
            this.zoom = 10;
        } else if (high > 20) {
            this.zoom = 12;
        } else {
            this.zoom = 10;
        }

        adjustToZoom();
        this.bars.clear();
        this.bars.addAll(quotes);
        this.bars.sort(new Quote.TimeComparator());
        createCandleSticks();
    }

    @Override
    public void draw(Canvas g) {
        renderer.setCanvas(g);
        if (renderer.sizeChanged(g.getWidth(), g.getHeight())) {
            canvasHeight = g.getHeight();
            canvasWidth = g.getWidth();
            camera.setHeight(g.getHeight());
            camera.setWidth(g.getWidth());

            setUp();
            updateIndicators();
        }

        renderer.Render(objects);
        g.setColor("#ffffff");
    }

    private void refresh() {
        objects.clear();
        objects.add(waterMark);
        objects.add(copyright);


        if (volumeIndicator != null) {
            objects.add(volumeIndicator);
        }


        if (timeIndicator != null) {
            objects.add(timeIndicator);
        }

        if (candleSticksIndicator != null) {
            objects.add(candleSticksIndicator);
        }

        objects.add(priceScale);

        if (volumeProfileIndicator != null) {
            objects.add(volumeProfileIndicator);
        }

    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
        startY = e.getY();
        startX = e.getX();

        if (startX > camera.getWidth() - 50) {
            scaling = true;
        } else if (startY > camera.getHeight() - 50) {
            stretching = true;
        } else {
            int x = e.getX();
            int y = e.getY();

            int mouseX = (int) camera.getMouseX(x);
            int mouseY = (int) camera.getMouseY(y);
            System.out.println("mouse x " + mouseX + " mouse y " + mouseY);
        }

    }

    @Override
    public void mouseReleased(MouseEvent e) {

        int dy = startY - e.getY();
        int dx = startX - e.getX();

        if (scaling) {
            if (dy > 2) {
                scaleUp(1);
            }
            if (dy < -2) {
                scaleDown(1);
            }

            scaling = false;
            return;
        }

        if (stretching) {
            if (dx > 2) {
                scaleUp(0);
            }
            if (dx < -2) {
                scaleDown(0);
            }

            stretching = false;
        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (scaling || stretching) {
            return;
        }

        int dy = startY - e.getY();
        int dx = startX - e.getX();

        int y = camera.getViewPortY() - (dy / 100);

        int x = camera.getViewPortX() - (dx / 100);

        camera.setViewPortY(y);
        camera.setViewPortX(x);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
    }

    @Override
    public void setWaterMark(String waterMark) {
        this.waterMarkText = waterMark;
        this.waterMark.setText(waterMarkText);
        this.waterMark.setColor("#003D7A");

    }

    @Override
    public void setTitle(String title) {
        this.titleText = title;
        topBar.setLabelText(title + " " + percentile + "%");
    }


    public void scaleUp(int what) {
        switch (what) {
            case 0 -> {
                this.barWidth += 2;
                if (this.barWidth > 20) {
                    this.barWidth = 20;
                    return;
                }
            }
            case 1 -> {
                this.zoom++;
                if (this.zoom > ZoomHelper.getMax()) {
                    this.zoom = ZoomHelper.getMax();
                    return;
                }
            }
        }

        adjustToZoom();

        createCandleSticks();
    }

    public void scaleDown(int what) {
        switch (what) {
            case 0 -> {
                this.barWidth -= 2;
                if (this.barWidth < 1) {
                    this.barWidth = 1;
                    return;
                }
            }
            case 1 -> {
                this.zoom--;
                if (this.zoom < 0) {
                    this.zoom = 0;
                    return;
                }
            }
        }

        adjustToZoom();

        createCandleSticks();
    }


    @Override
    public void onOptionsSelected(OptionsMenu.OptionItem options) {
        if (options == OptionsMenu.OptionItem.CENTER_CHART) {
            centerCamera();
        }
    }

    public void addVolumeProfile(List<Quote> quotes) {
        this.volumeProfile.clear();
        this.volumeProfile.addAll(quotes);

        createVolumeProfile();
    }

    private void adjustToZoom() {

        System.out.println("Zoom " + zoom);

        scale = ZoomHelper.getScalar(zoom);
    }

    private void updateIndicators() {
        priceScale.setScale(scale);

        volumeIndicator = null;
        volumeProfileIndicator = null;
        timeIndicator = null;

        if (candleSticksIndicator == null) {
            return;
        }

        createVolumeProfile();

        new VolumeIndicatorCreator().create(
                this,
                candleSticksIndicator.getCandleSticks(),
                150,
                canvasHeight - 20);

        new TimeCreator().create(this,
                candleSticksIndicator.getCandleSticks(),
                canvasHeight,
                20);

    }

    private void adjustCamera() {
        camera.setViewPortY((-loadY - camera.getHeight()) * -1);
    }

    @Override
    public void onCandleSticksIndicatorCreated(CandleSticksIndicator candleSticksIndicator, int x, int y) {
        this.candleSticksIndicator = candleSticksIndicator;
        this.lastBar = x;
        this.loadY = y;

        SwingUtilities.invokeLater(() -> {
            refresh();
            centerCamera();
            updateIndicators();
        });
    }

    @Override
    public void onVolumeIndicatorCreated(VolumeIndicator volumeIndicator) {
        this.volumeIndicator = volumeIndicator;
        refresh();
    }

    @Override
    public void onVolumeProfileCreated(VolumeProfileIndicator volumeProfileIndicator) {
        this.volumeProfileIndicator = volumeProfileIndicator;
        this.volumeProfileIndicator.setzOrder(1);
        refresh();
    }

    @Override
    public void onTimeIndicatorCreated(TimeIndicator timeIndicator) {
        this.timeIndicator = timeIndicator;
        refresh();
    }


    private void createVolumeProfile() {
        if (volumeProfileIndicator != null) {
            volumeProfileIndicator = null;
        }

        new VolumeProfileCreator().create(this,
                this.volumeProfile,
                scale,
                150,
                camera.getWidth() - 200
        );
    }

    private void createCandleSticks() {
        candleSticksIndicator = null;

        new CandleSticksCreator().create(
                this,
                bars,
                scale,
                barWidth,
                barSeparator
        );
    }

    private void reset() {
        bars.clear();
    }

    private void centerCamera() {
        System.out.println("x " + lastBar + " y" + loadY);
        camera.setViewPortY((-loadY - camera.getHeight() / 2) * -1);
        camera.setViewPortX((lastBar - camera.getWidth() / 2) * -1);
    }
}