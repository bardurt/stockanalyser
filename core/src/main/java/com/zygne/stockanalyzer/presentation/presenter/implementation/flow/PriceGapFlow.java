package com.zygne.stockanalyzer.presentation.presenter.implementation.flow;

import com.zygne.stockanalyzer.domain.executor.Executor;
import com.zygne.stockanalyzer.domain.executor.MainThread;
import com.zygne.stockanalyzer.domain.interactor.implementation.data.GapHistoryInteractorImpl;
import com.zygne.stockanalyzer.domain.interactor.implementation.data.PriceGapInteractorImpl;
import com.zygne.stockanalyzer.domain.interactor.implementation.data.base.GapHistoryInteractor;
import com.zygne.stockanalyzer.domain.interactor.implementation.data.base.PriceGapInteractor;
import com.zygne.stockanalyzer.domain.model.GapHistory;
import com.zygne.stockanalyzer.domain.model.Histogram;
import com.zygne.stockanalyzer.domain.model.PriceGap;

import java.util.List;

public class PriceGapFlow implements PriceGapInteractor.Callback,
        GapHistoryInteractor.Callback {

    private final Executor executor;
    private final MainThread mainThread;
    private final Callback callback;
    private List<Histogram> histogramList;
    private final GapType gapType;

    public PriceGapFlow(Executor executor, MainThread mainThread, Callback callback, List<Histogram> histogramList, GapType gapType) {
        this.executor = executor;
        this.mainThread = mainThread;
        this.callback = callback;
        this.histogramList = histogramList;
        this.gapType = gapType;
    }

    public void start(){
        new PriceGapInteractorImpl(executor, mainThread, this, histogramList).execute();
    }

    @Override
    public void onPriceGapsFound(List<PriceGap> data) {
        callback.onPriceGapsGenerated(data, gapType);
        new GapHistoryInteractorImpl(executor, mainThread, this, histogramList).execute();
    }

    @Override
    public void onGapHistoryCompleted(GapHistory gapHistory) {
        callback.onGapHistoryGenerated(gapHistory);
    }

    public interface Callback{
        void onPriceGapsGenerated(List<PriceGap> data, GapType gapType);
        void onGapHistoryGenerated(GapHistory gapHistory);
    }

    public enum GapType{
        DAILY,
        INTRA_DAY
    }
}
