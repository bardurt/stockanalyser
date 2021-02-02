package com.zygne.stockanalyzer.presentation.presenter.implementation.delegates;

import com.zygne.stockanalyzer.IbDataBroker;
import com.zygne.stockanalyzer.domain.api.Api;
import com.zygne.stockanalyzer.domain.api.DataBroker;
import com.zygne.stockanalyzer.domain.executor.Executor;
import com.zygne.stockanalyzer.domain.executor.MainThread;
import com.zygne.stockanalyzer.domain.interactor.implementation.data.*;
import com.zygne.stockanalyzer.domain.interactor.implementation.data.base.*;
import com.zygne.stockanalyzer.domain.model.*;
import com.zygne.stockanalyzer.domain.model.enums.TimeInterval;
import com.zygne.stockanalyzer.presentation.presenter.base.MainPresenter;
import com.zygne.stockanalyzer.presentation.presenter.implementation.delegates.flow.DailySupplyFlow;
import com.zygne.stockanalyzer.presentation.presenter.implementation.delegates.flow.DailyVolumeFlow;
import com.zygne.stockanalyzer.presentation.presenter.implementation.delegates.flow.SupplyFlow;

import java.util.ArrayList;
import java.util.List;

public class InteractiveBrokersDelegate implements MainPresenter,
        DataFetchInteractor.Callback,
        CacheWriteInteractor.Callback,
        CacheReadInteractor.Callback,
        CacheCheckerInteractor.Callback,
        MissingDataInteractor.Callback,
        DataMergeInteractor.Callback,
        com.zygne.stockanalyzer.domain.interactor.implementation.data.base.HistogramInteractor.Callback,
        SupplyFlow.Callback,
        FundamentalsInteractor.Callback,
        AverageBarVolumeInteractor.Callback,
        PriceGapInteractor.Callback,
        LiquiditySideInteractor.Callback,
        LiquiditySideFilterInteractor.Callback,
        LiquiditySidePriceInteractor.Callback,
        Api.ConnectionListener,
        DailyVolumeFlow.Callback {

    private final DataBroker dataBroker;

    private boolean connected = false;
    private final View view;
    private List<Histogram> histogramList;
    private String ticker;
    private double percentile = 0;
    private Fundamentals fundamentals;

    private TimeInterval timeInterval = TimeInterval.Five_Minutes;
    private int monthsToFetch = 24;
    private boolean downloadingData = false;
    private final Executor executor;
    private final MainThread mainThread;

    private final List<BarData> cachedData = new ArrayList<>();
    private final List<BarData> downloadedData = new ArrayList<>();

    private final SupplyFlow supplyFlow;

    private final Settings settings;
    private String dateRange = "";

    public InteractiveBrokersDelegate(Executor threadExecutor, MainThread mainThread, View view, Settings settings) {
        this.executor = threadExecutor;
        this.mainThread = mainThread;
        this.dataBroker = new IbDataBroker();
        dataBroker.setConnectionListener(this);
        this.view = view;
        this.settings = settings;
        this.supplyFlow = new SupplyFlow(executor, mainThread, this);
        List<TimeInterval> timeIntervals = new ArrayList<>();
        timeIntervals.add(TimeInterval.One_Minute);
        timeIntervals.add(TimeInterval.Five_Minutes);
        timeIntervals.add(TimeInterval.Fifteen_Minutes);
        timeIntervals.add(TimeInterval.Thirty_Minutes);
        timeIntervals.add(TimeInterval.Hour);
        timeIntervals.add(TimeInterval.Day);
        timeIntervals.add(TimeInterval.Week);
        view.onTimeFramesPrepared(timeIntervals, 2);

        List<DataSize> dataSize = new ArrayList<>();
        dataSize.add(new DataSize(1, DataSize.Unit.Year));
        dataSize.add(new DataSize(2, DataSize.Unit.Year));
        dataSize.add(new DataSize(3, DataSize.Unit.Year));
        dataSize.add(new DataSize(4, DataSize.Unit.Year));
        dataSize.add(new DataSize(5, DataSize.Unit.Year));
        view.onDataSizePrepared(dataSize, dataSize.size() - 1);

        view.toggleConnectionSettings(true);


        List<ViewComponent> viewComponents = new ArrayList<>();

        viewComponents.add(ViewComponent.VPA);
        viewComponents.add(ViewComponent.INTRA_DAY);
        viewComponents.add(ViewComponent.WICKS);
        viewComponents.add(ViewComponent.PRICE_GAPS);
        viewComponents.add(ViewComponent.SCRIPT);

        view.prepareView(viewComponents);
    }

    @Override
    public void getZones(String ticker, double percentile, TimeInterval timeInterval, int monthsToFetch, boolean fundamentalData) {
        if (!connected) {
            view.showError("Client not connected!");
            return;
        }

        if (downloadingData) {
            return;
        }

        if (ticker.isEmpty()) {
            view.showError("No Symbol name!");
            return;
        }

        downloadedData.clear();
        cachedData.clear();

        this.percentile = percentile;
        this.timeInterval = timeInterval;
        this.monthsToFetch = monthsToFetch;

        downloadingData = true;
        this.ticker = ticker.replaceAll("\\s+", "");

        view.showError("");

        view.showLoading("Fetching data for " + ticker.toUpperCase() + "");

        new CacheCheckerInteractorImpl(executor, mainThread, this, settings.getCache(), ticker + "-" + timeInterval.name()).execute();

    }

    @Override
    public void onCachedDataFound(String location) {
        view.showLoading("Reading cached data...");
        new CacheReadInteractorImpl(executor, mainThread, this, location).execute();
    }

    @Override
    public void onCachedDataError() {
        new DataFetchInteractorImpl(executor, mainThread, this, ticker, timeInterval, new DataSize(monthsToFetch, DataSize.Unit.Year), dataBroker).execute();
    }

    @Override
    public void onCachedDataRead(List<BarData> entries, long timeStamp) {
        cachedData.addAll(entries);
        long difference = System.currentTimeMillis() - timeStamp;

        long dayMs = 12 * 3600 * 1000;

        if (difference < dayMs) {
            new HistogramInteractorImpl(executor, mainThread, this, entries).execute();
        } else {
            new MissingDataInteractorImpl(executor, mainThread, this, entries).execute();
        }
    }

    @Override
    public void onDataCached(List<BarData> lines) {
        new HistogramInteractorImpl(executor, mainThread, this, lines).execute();
    }

    @Override
    public void onDataMerged(List<BarData> entries) {
        new CacheWriteInteractorImpl(executor, mainThread, this, settings.getCache(), ticker + "-" + timeInterval.name(), entries).execute();

    }

    @Override
    public void onDataFetched(List<BarData> entries, String timestamp) {
        downloadedData.addAll(entries);
        new DataMergeInteractorImpl(executor, mainThread, this, downloadedData, cachedData).execute();
    }

    @Override
    public void onMissingDataCalculated(int daysMissing) {
        System.out.println("Missing days " + daysMissing);
        if (daysMissing > 0) {
            view.showLoading("Fetching missing data");
            new DataFetchInteractorImpl(executor, mainThread, this, ticker, timeInterval, new DataSize(daysMissing, DataSize.Unit.Day), dataBroker).execute();
        } else {
            new HistogramInteractorImpl(executor, mainThread, this, cachedData).execute();
        }
    }

    @Override
    public void onDataFetchError(String message) {
        downloadingData = false;
        view.hideLoading();
        view.showError(message);
    }

    @Override
    public void onStatusUpdate(String message) {
        view.onStatusUpdate(message);
    }

    @Override
    public void onHistogramCreated(List<Histogram> data) {
        this.histogramList = data;
        dateRange = histogramList.get(histogramList.size() - 1).dateTime + " - " + histogramList.get(0).dateTime;
        supplyFlow.start(histogramList, percentile);
    }


    @Override
    public void onSupplyCompleted(List<LiquidityLevel> data) {
        view.onResistanceFound(data);

        downloadingData = false;
        view.hideLoading();

        new PriceGapInteractorImpl(executor, mainThread, this, histogramList).execute();
    }

    @Override
    public void onFundamentalsFetched(Fundamentals fundamentals) {
        this.fundamentals = fundamentals;
        view.hideLoading();
        new AverageBarVolumeInteractorImpl(executor, mainThread, this, histogramList).execute();
    }

    @Override
    public void onAverageBarVolumeCalculated(long avgVol) {
        fundamentals.setAvgVol(avgVol);
        view.onFundamentalsLoaded(fundamentals);
        downloadingData = false;
        view.onComplete(ticker, timeInterval.toString(), dateRange);
    }

    @Override
    public void onApiConnected() {
        connected = true;
        mainThread.post(view::onConnected);
    }

    @Override
    public void onApiDisconnected() {
        connected = false;
        mainThread.post(view::onDisconnected);
    }

    @Override
    public void toggleConnection() {
        if (!connected) {
            dataBroker.connect();
        } else {
            dataBroker.disconnect();
        }
    }

    @Override
    public void findHighVolume() {
        new DailyVolumeFlow(executor, mainThread, this, dataBroker).findVolume(ticker);
    }

    @Override
    public void onPriceGapsFound(List<PriceGap> data) {
        view.hideLoading();
        view.onPriceGapsFound(data);
        view.onComplete(ticker, timeInterval.toString(), dateRange);
        findHighVolume();
    }

    @Override
    public void onLiquiditySidesFiltered(List<LiquiditySide> data) {

    }

    @Override
    public void onLiquiditySidesCreated(List<LiquiditySide> data) {

    }

    @Override
    public void onLiquiditySideForPriceFound(List<LiquiditySide> data) {

    }

    @Override
    public void onDailyHighVolumeFound(List<VolumeBarDetails> data, List<Histogram> histograms) {
        view.onHighVolumeBarFound(data);
    }

}
