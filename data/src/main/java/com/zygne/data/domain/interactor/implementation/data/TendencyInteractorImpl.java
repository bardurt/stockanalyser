package com.zygne.data.domain.interactor.implementation.data;

import com.zygne.data.domain.interactor.implementation.data.base.TendencyInteractor;
import com.zygne.data.domain.model.Histogram;
import com.zygne.data.domain.model.Tendency;
import com.zygne.data.domain.model.TendencyEntry;
import com.zygne.data.domain.model.TendencyReport;
import com.zygne.data.domain.utils.TimeHelper;
import com.zygne.arch.domain.executor.Executor;
import com.zygne.arch.domain.executor.MainThread;
import com.zygne.arch.domain.interactor.base.BaseInteractor;

import java.util.ArrayList;
import java.util.List;

public class TendencyInteractorImpl extends BaseInteractor implements TendencyInteractor {

    private final Callback callback;
    private final List<Histogram> histogramList;
    private final List<Long> timeStamps = new ArrayList<>();

    public TendencyInteractorImpl(Executor executor, MainThread mainThread,
                                  Callback callback, List<Histogram> histogramList) {
        super(executor, mainThread);
        this.callback = callback;
        this.histogramList = histogramList;
    }

    @Override
    public void run() {
        histogramList.sort(new Histogram.TimeComparator());

        int endYear = TimeHelper.getYearFromTimeStamp(System.currentTimeMillis());
        int yearToFetch = TimeHelper.getYearFromTimeStamp(histogramList.get(0).timeStamp);

        List<List<Histogram>> histogramsByYear = new ArrayList<>();
        List<Histogram> histogramYear = new ArrayList<>();

        int minLength = Integer.MAX_VALUE;

        for (Histogram histogram : histogramList) {
            int year = TimeHelper.getYearFromTimeStamp(histogram.timeStamp);

            if (year == yearToFetch) {
                histogramYear.add(histogram);
            } else {
                yearToFetch++;
                if (year != endYear) {
                    if (minLength > histogramYear.size()) {
                        minLength = histogramYear.size();
                    }
                }
                histogramsByYear.add(histogramYear);
                histogramYear = new ArrayList<>();
            }
        }

        histogramsByYear.add(histogramYear);

        List<List<Histogram>> normalList = new ArrayList<>();

        List<Histogram> currentYear = new ArrayList<>(histogramsByYear.remove(histogramsByYear.size() - 1));

        // remove first year, to ensure complete data
        histogramsByYear.remove(0);

        // normalize the list to make sure all have same size
        for (List<Histogram> current : histogramsByYear) {
            if (current.size() > minLength) {
                List<Histogram> data = current.subList(0, minLength - 1);
                data.add(current.get(current.size() - 1));
                normalList.add(data);
            } else {
                normalList.add(current);
            }
        }

        List<List<TendencyEntry>> avgList = new ArrayList<>();

        for (List<Histogram> histograms : normalList) {
            avgList.add(getChange(histograms));
        }

        List<TendencyEntry> currentYearAvg = getChange(currentYear);

        for (int i = 0; i < currentYearAvg.size(); i++) {
            currentYearAvg.get(i).timeStamp = avgList.get(0).get(i).timeStamp;
        }

        for (TendencyEntry h : avgList.get(0)) {
            timeStamps.add(h.timeStamp);
        }

        TendencyReport report = new TendencyReport(new ArrayList<>());
        report.tendencies().add(new Tendency("Current Year", currentYearAvg));

        if (avgList.size() >= 20) {
            report.tendencies().add(new Tendency("5 Year", getAverageFor(avgList, minLength, 5)));
            report.tendencies().add(new Tendency("10 Year", getAverageFor(avgList, minLength, 10)));
            report.tendencies().add(new Tendency("20 Year", getAverageFor(avgList, minLength, 20)));
        } else if (avgList.size() >= 7) {
            report.tendencies().add(new Tendency("3 Year", getAverageFor(avgList, minLength, 3)));
            report.tendencies().add(new Tendency("5 Year", getAverageFor(avgList, minLength, 5)));
            report.tendencies().add(new Tendency("7 Year", getAverageFor(avgList, minLength, 7)));
        }

        mainThread.post(() -> callback.omTendencyReportCreated(report));
    }

    private List<TendencyEntry> getAverageFor(List<List<TendencyEntry>> data, int minLength, int years) {
        if (data.size() < years) {
            throw new RuntimeException("Data size " + data.size() + " is less than " + years);
        }

        List<TendencyEntry> yearAvg = new ArrayList();

        int max = data.size() - 1;
        int end = max - years;

        for (int i = 0; i < minLength; i++) {
            TendencyEntry current = new TendencyEntry();
            double sum = 0.0d;

            for (int j = max; j > end; j--) {
                sum += data.get(j).get(i).value;
            }

            double avg = sum / years;
            current.timeStamp = timeStamps.get(i);
            current.value = avg;

            yearAvg.add(current);
        }

        return yearAvg;
    }


    private List<TendencyEntry> getChange(List<Histogram> data) {
        List<TendencyEntry> avgByYearList = new ArrayList<>();
        TendencyEntry start = new TendencyEntry();
        start.value = 0;
        start.timeStamp = data.get(0).timeStamp;
        avgByYearList.add(start);
        double startValue = data.get(0).open;
        for (int j = 1; j < data.size(); j++) {
            double endValue = data.get(j).open;
            double change = ((endValue - startValue) / startValue) * 100;

            TendencyEntry currentAvg = new TendencyEntry();
            currentAvg.value = change;
            currentAvg.timeStamp = data.get(j).timeStamp;
            avgByYearList.add(currentAvg);

        }

        return avgByYearList;
    }

}