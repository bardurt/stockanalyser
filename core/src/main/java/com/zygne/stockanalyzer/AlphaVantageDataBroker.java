package com.zygne.stockanalyzer;

import com.zygne.stockanalyzer.domain.api.DataBroker;
import com.zygne.stockanalyzer.domain.exceptions.ApiCallExceededException;
import com.zygne.stockanalyzer.domain.model.BarData;
import com.zygne.stockanalyzer.domain.model.DataSize;
import com.zygne.stockanalyzer.domain.model.enums.TimeInterval;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

public class AlphaVantageDataBroker implements DataBroker {

    private static final String ERROR_INPUT = "Thank you for using Alpha Vantage!";

    private static final String FILE_TERMINATOR = ",,,,,";
    private static final String TIME_SERIES_DAILY = "TIME_SERIES_DAILY";
    private static final String TIME_SERIES_WEEKLY = "TIME_SERIES_WEEKLY";
    private static final String TIME_SERIES_MONTHLY = "TIME_SERIES_MONTHLY";
    private static final int MAX_FAILURES = 3;

    private static final long TIME_TO_SLEEP = 15000;
    private final String apiKey;
    private int failures = 0;

    private Callback callback;

    public AlphaVantageDataBroker(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public void downloadHistoricalBarData(String symbol, DataSize dataSize, TimeInterval timeInterval) {

        final String interval;

        if (timeInterval == TimeInterval.One_Minute) {
            interval = "1min";
        } else if (timeInterval == TimeInterval.Three_Minutes) {
            interval = "3min";
        } else if (timeInterval == TimeInterval.Five_Minutes) {
            interval = "5min";
        } else if (timeInterval == TimeInterval.Fifteen_Minutes) {
            interval = "15min";
        } else if (timeInterval == TimeInterval.Thirty_Minutes) {
            interval = "30min";
        } else if (timeInterval == TimeInterval.Hour) {
            interval = "60min";
        } else {
            interval = null;
        }


        final int monthsToFetch = dataSize.getSize();

        if (interval != null) {
            System.out.println(symbol + " " + interval + " " + dataSize.getSize());

            Thread t = new Thread(() -> {
                List<BarData> data = fetchIntraDay(symbol, interval, monthsToFetch, apiKey);
                if(callback != null){
                    callback.onDataFinished(data);
                }
            });

            t.start();
        } else {

            final String timeSeries;

            if (timeInterval == TimeInterval.Day) {
                timeSeries = TIME_SERIES_DAILY;
            } else {
                timeSeries = TIME_SERIES_WEEKLY;
            }

            System.out.println(symbol + " " + timeSeries + " " + dataSize.getSize());


            Thread t = new Thread(() -> {
                List<BarData> data = downLoadTimeSeries(symbol, timeSeries, apiKey);
                if(callback != null){
                    callback.onDataFinished(data);
                }
            });

            t.start();
        }
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
    public void connect() { }

    @Override
    public void disconnect() { }

    @Override
    public void setConnectionListener(ConnectionListener connectionListener) { }

    @Override
    public void removeConnectionListener() { }

    private List<BarData> fetchIntraDay(String symbol, String interval, int monthsToFetch, String apiKey) {
        List<BarData> lines = new ArrayList<>();

        // Flag to check if downloading data is finished or not
        boolean finished = false;

        // start by downloading data for the most recent year.
        int year = 1; // year 1 month 1 is the most recent month

        // counter for checking how many months have been downloaded
        int currentCount = 0;

        while (year < 3) {
            // start with download the latest month of year
            int month = 1;

            // loop through all the month for the year
            while (month < 13) {

                String time = "year" + year + "month" + month;
                List<BarData> data = null;
                try {
                    data = downloadIntraDay(symbol, interval, apiKey, time);
                    if (data.isEmpty()) {
                        finished = true;
                        break;
                    }
                    lines.addAll(data);
                    failures = 0;
                } catch (ApiCallExceededException e) {
                    month--;
                    currentCount--;
                    failures++;
                    if (failures > MAX_FAILURES) {
                        finished = true;
                    }
                }

                // finish this month
                month++;

                // add this month to total count
                currentCount++;

                System.out.println("Downloaded page : " + currentCount);
                // check if we have fetched all the months requested
                if (currentCount >= monthsToFetch) {
                    finished = true;
                }

                // we are finished, so no need to continue
                if (finished) {
                    break;
                }

                try {
                    Thread.sleep(TIME_TO_SLEEP);
                } catch (InterruptedException ignored) {
                }
            }

            // we are finished, so no need to continue
            if (finished) {
                break;
            }

            // finish this year
            year++;
        }

        System.out.println("Downloaded complete : " + currentCount);
        return lines;
    }

    /**
     * Method to download Intra day Historical data from AlphaVantage (AV)
     *
     * @param symbol   : the Symbol (ticker)
     * @param interval : time interval for historical data (1 min, 3 min)
     * @param apiKey   : Api key for AV
     */
    private List<BarData> downloadIntraDay(String symbol, String interval, String apiKey, String time) throws ApiCallExceededException {

        // container for the read historical data
        List<BarData> lines = new ArrayList<>();

        InputStreamReader inputStreamReader = null;
        BufferedReader bufferedReader = null;
        URLConnection urlConnection;


        String url = "https://www.alphavantage.co/query?function=TIME_SERIES_INTRADAY_EXTENDED&symbol=" + symbol.toUpperCase() + "&interval=" + interval + "&slice=" + time + "&apikey=" + apiKey;
        try {
            // prepare the url for Alpha Vantage
            URL content = new URL(url);

            // open connection to URL
            urlConnection = content.openConnection();

            // fetch stream for reading data
            inputStreamReader = new InputStreamReader(urlConnection.getInputStream());

            // reader to read data from stream
            bufferedReader = new BufferedReader(inputStreamReader);

            // the line which is currently being read
            String line;

            while ((line = bufferedReader.readLine()) != null) {
                if (line.equalsIgnoreCase(FILE_TERMINATOR)) {
                    break;
                }

                if(!validateInput(line)){
                    try {
                        bufferedReader.close();
                        bufferedReader = null;
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    try {
                        inputStreamReader.close();
                        inputStreamReader = null;
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }

                    throw new ApiCallExceededException();
                }

                // clean up the read data
                String data = splitString(line);

                // if the read data is null, then it is most likely a header
                if (data != null) {
                   BarData barData = BarData.fromStream(data);
                   if(barData != null){
                       barData.setDataFarm(BarData.DataFarm.ALPHA_VANTAGE);
                       lines.add(barData);
                   }
                }
            }

            // clean up connection
            bufferedReader.close();
            inputStreamReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                    bufferedReader = null;
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
            if (inputStreamReader != null) {
                try {
                    inputStreamReader.close();
                    inputStreamReader = null;
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }

        return lines;
    }

    private List<BarData> downLoadTimeSeries(String symbol, String series, String apiKey) {

        List<BarData> lines = new ArrayList<>();

        String url = "https://www.alphavantage.co/query?function=" + series + "&symbol=" + symbol + "&outputsize=full&apikey=" + apiKey + "&datatype=csv";

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
                String data = splitString(line);
                // if the read data is null, then it is most likely a header
                if (data != null) {
                    BarData barData = BarData.fromStream(data);
                    if(barData != null){
                        barData.setDataFarm(BarData.DataFarm.ALPHA_VANTAGE);
                        lines.add(barData);
                    }
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

    private String splitString(String line) {

        String[] parts = line.split(",", -1);

        if (parts[0].equalsIgnoreCase("time")) {
            return null;
        }

        if (parts[0].equalsIgnoreCase("timestamp")) {
            return null;
        }

        if (parts.length < 5) {
            return null;
        }

        return line;
    }

    private boolean validateInput(String input){

        if(input.contains(ERROR_INPUT)){
            System.out.println(input);
            return false;
        }

        return true;
    }

}
