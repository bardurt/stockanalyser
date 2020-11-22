package com.zygne.stockalyze.domain.interactor.implementation.data.base;

import com.zygne.stockalyze.domain.interactor.base.Interactor;
import com.zygne.stockalyze.domain.model.LiquidityLevel;

import java.util.List;

public interface PivotInteractor extends Interactor {

    interface Callback{
        void onPivotsFound(List<LiquidityLevel> data);
    }
}
