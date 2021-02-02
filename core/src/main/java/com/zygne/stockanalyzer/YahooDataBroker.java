package com.zygne.stockanalyzer;

import com.zygne.stockanalyzer.domain.api.DataBroker;
import com.zygne.stockanalyzer.domain.model.BarData;
import com.zygne.stockanalyzer.domain.model.DataSize;
import com.zygne.stockanalyzer.domain.model.enums.TimeInterval;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class YahooDataBroker implements DataBroker {

    private Callback callback;

    @Override
    public void downloadHistoricalBarData(String symbol, DataSize dataSize, TimeInterval timeInterval) {

        final int years = dataSize.getSize();

        Calendar calendar = Calendar.getInstance();
        String timeEnd = "" + (calendar.getTime().getTime() / 1000);

        calendar.add(Calendar.YEAR, years * -1);

        String timeStart = "" + (calendar.getTime().getTime() / 1000);

        String time = "1d";

        if(timeInterval == TimeInterval.Day){
            time = "1d";
        } else  if(timeInterval == TimeInterval.Week){
            time = "1wk";
        }

        String url = "https://query1.finance.yahoo.com/v7/finance/download/" + symbol + "?period1=" + timeStart + "&period2=" + timeEnd + "&interval=" + time + "&events=history&includeAdjustedClose=false";

        System.out.println(url);

        Thread t = new Thread(() -> {
            List<BarData> data = downLoadTimeSeries(url);
            if(callback != null){
                callback.onDataFinished(data);
            }
        });

        t.start();
    }

    private List<BarData> downLoadTimeSeries(String url) {

        List<BarData> lines = new ArrayList<>();

        InputStreamReader inputStreamReader = null;
        BufferedReader bufferedReader = null;
        URLConnection urlConnection = null;
        try {
            URL content = new URL(url);

            // establish connection to file in URL
            urlConnection = content.openConnection();

            inputStreamReader = new InputStreamReader(urlConnection.getInputStream());

            bufferedReader = new BufferedReader(inputStreamReader);

            String line;

            while ((line = bufferedReader.readLine()) != null) {

                BarData barData = BarData.fromStream(line, BarData.DataFarm.YAHOO);
                if (barData != null) {
                    barData.setDataFarm(BarData.DataFarm.YAHOO);
                    lines.add(barData);
                }

            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (inputStreamReader != null) {
                try {
                    inputStreamReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return lines;
    }

    @Override
    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    @Override
    public void removeCallback() {
        this.callback = null;
    }

    @Override
    public void connect() {
    }

    @Override
    public void disconnect() {
    }

    @Override
    public void setConnectionListener(ConnectionListener connectionListener) {
    }

    @Override
    public void removeConnectionListener() {
    }

    public static void main(String[] args) {

        YahooDataBroker dataBroker = new YahooDataBroker();

        dataBroker.downloadHistoricalBarData("IBM", new DataSize(1, DataSize.Unit.Year), TimeInterval.Day);
    }
}
