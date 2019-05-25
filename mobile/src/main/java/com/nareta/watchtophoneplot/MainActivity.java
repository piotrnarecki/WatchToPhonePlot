package com.nareta.watchtophoneplot;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

    //PLOTS

    private static final String TAG = "MainActivity";

    //
    int progress = 0;


    //wykres
    private LineChart xChart;
    private LineChart yChart;
    private LineChart zChart;
    private LineChart hrChart;


    private int pointsOnChart = 60;   // przy oswiezaniu co 500ms daje na wykresie podglad z 30s


    private int refreshTime = 500;


    private Thread messageThread;
    private boolean plotData = true;


    //obiekty GUI
    private TextView textview;
    private TextView timeOutput;
    private Switch measureTypeSwitch;

    private Button detailsButton;
    private Button startButton;
    private Button stopButton;
    private Button restartButton;


    protected Handler myHandler;


    int sentMessageNumber = 1;


    //zamiana wiadomosci na dane
    private String[] messageArray;

    //dane do wykresów
    float xAcc;
    float yAcc;
    float zAcc;
    float heartRate;


    //watek zapisujacy
    private int measureTime = 120; //sekundy //zamienic na 120 (bo mierzy co 500ms a pomiar ma byc prowadzony przez minute)
    private int timeBetween = 500; //milisekundy

    private int seconds = measureTime;
    private boolean running;
    private boolean wasRunning;

    private Handler timerHandler = new Handler();
    private Runnable timerRunnable;

    //tablice do zapisywania
    private List<Double> xAccList = new ArrayList<>();
    private List<Double> yAccList = new ArrayList<>();
    private List<Double> zAccList = new ArrayList<>();
    private List<Double> heartRateList = new ArrayList<>();

    //zapis
    private String measureType;
    private String ID;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); //  aplikacja dziala tylko w pionie

//referecje
        textview = (TextView) findViewById(R.id.textView);
        timeOutput = (TextView) findViewById(R.id.timeOutput);
        measureTypeSwitch = (Switch) findViewById(R.id.measureTypeSwitch);

        detailsButton = (Button) findViewById(R.id.detailsButton);
        startButton = (Button) findViewById(R.id.startButton);
        stopButton = (Button) findViewById(R.id.stopButton);
        restartButton = (Button) findViewById(R.id.restartButton);

        //dane z poprzedniej aktywności
        Bundle data = getIntent().getExtras();
        if (data != null) {
            ID = data.getString("ID");
        }


//pozwolenia
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1000);
        }

        //watek zapisujacy
        if (savedInstanceState != null) {
            seconds = savedInstanceState.getInt("seconds");
            running = savedInstanceState.getBoolean("running");
            wasRunning = savedInstanceState.getBoolean("wasRunning");
        }

        runTimer();


        myHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                Bundle stuff = msg.getData();
                messageText(stuff.getString("messageText"));
                return true;
            }
        });


        IntentFilter messageFilter = new IntentFilter(Intent.ACTION_SEND);
        Receiver messageReceiver = new Receiver();
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, messageFilter);


        //PLOTS

//referencje
        xChart = (LineChart) findViewById(R.id.chart1);
        yChart = (LineChart) findViewById(R.id.chart2);
        zChart = (LineChart) findViewById(R.id.chart3);
        hrChart = (LineChart) findViewById(R.id.chart4);

//podpisy
        xChart.getDescription().setEnabled(true);
        xChart.getDescription().setText("X axis linear acceleration");
        xChart.getDescription().setTextColor(Color.WHITE);

        yChart.getDescription().setEnabled(true);
        yChart.getDescription().setText("Y axis linear acceleration");
        yChart.getDescription().setTextColor(Color.WHITE);

        zChart.getDescription().setEnabled(true);
        zChart.getDescription().setText("Z axis linear acceleration");
        zChart.getDescription().setTextColor(Color.WHITE);

        hrChart.getDescription().setEnabled(true);
        hrChart.getDescription().setText("heart rate");
        hrChart.getDescription().setTextColor(Color.WHITE);

////////////////////////X PLOT////////////////////////


        xChart.setTouchEnabled(false);///// tutaj
        xChart.setDragEnabled(false);///// tutaj
        xChart.setScaleEnabled(false);
        xChart.setDrawGridBackground(false);
        xChart.setPinchZoom(false); ///// tutaj


        //data
        LineData xData = new LineData();
        xData.setValueTextColor(Color.WHITE);
        xChart.setData(xData); //dodaj dane do wykresu


        Legend xChartLegend = xChart.getLegend();
        //xChartLegend.setForm(Legend.LegendForm.LINE);
        xChartLegend.setEnabled(false);


        XAxis xx1 = xChart.getXAxis();
        xx1.setTextColor(Color.WHITE);
        xx1.setDrawGridLines(true);
        xx1.setAvoidFirstLastClipping(false);//
        xx1.setEnabled(false);//////////////////////

        YAxis xLeftAxis = xChart.getAxisLeft();
        xLeftAxis.setTextColor(Color.WHITE);
        xLeftAxis.setDrawGridLines(false);

        //skala osi y
        xLeftAxis.setAxisMaximum(12f);
        xLeftAxis.setAxisMinimum(-12f);


        xLeftAxis.setDrawGridLines(true);

        YAxis xRightAxis = xChart.getAxisRight();
        xRightAxis.setEnabled(false);


        xChart.getAxisLeft().setDrawGridLines(false);
        xChart.getXAxis().setDrawGridLines(false);
        xChart.setDrawBorders(false);


////////////////////////Y PLOT////////////////////////


        yChart.setTouchEnabled(false);///// tutaj
        yChart.setDragEnabled(false);///// tutaj
        yChart.setScaleEnabled(false);
        yChart.setDrawGridBackground(false);
        yChart.setPinchZoom(false); ///// tutaj
        //yChart.setBackgroundColor(Color.WHITE);


        //data
        LineData yData = new LineData();
        yData.setValueTextColor(Color.WHITE);
        yChart.setData(yData); //dodaj dane do wykresu


        Legend yChartLegend = yChart.getLegend();
        // yChartLegend.setForm(Legend.LegendForm.LINE);
        yChartLegend.setEnabled(false);


        XAxis yx1 = yChart.getXAxis();
        yx1.setTextColor(Color.WHITE);
        yx1.setDrawGridLines(true);
        yx1.setAvoidFirstLastClipping(false);//
        yx1.setEnabled(false);

        YAxis yLeftAxis = yChart.getAxisLeft();
        yLeftAxis.setTextColor(Color.WHITE);
        yLeftAxis.setDrawGridLines(false);

        //skala osi y
        yLeftAxis.setAxisMaximum(12f);
        yLeftAxis.setAxisMinimum(-12f);


        yLeftAxis.setDrawGridLines(true);

        YAxis yRightAxis = yChart.getAxisRight();
        yRightAxis.setEnabled(false);


        yChart.getAxisLeft().setDrawGridLines(false);
        yChart.getXAxis().setDrawGridLines(false);
        yChart.setDrawBorders(false);


////////////////////////Z PLOT////////////////////////

        zChart.setTouchEnabled(false);///// tutaj
        zChart.setDragEnabled(false);///// tutaj
        zChart.setScaleEnabled(false);
        zChart.setDrawGridBackground(false);
        zChart.setPinchZoom(false); ///// tutaj
        //zChart.setBackgroundColor(Color.WHITE);


        //data
        LineData zData = new LineData();
        zData.setValueTextColor(Color.WHITE);
        zChart.setData(zData); //dodaj dane do wykresu


        Legend zChartLegend = zChart.getLegend();
        //zChartLegend.setForm(Legend.LegendForm.LINE);
        zChartLegend.setEnabled(false);

        XAxis zx1 = zChart.getXAxis();
        zx1.setTextColor(Color.WHITE);
        zx1.setDrawGridLines(true);
        zx1.setAvoidFirstLastClipping(false);//
        zx1.setEnabled(false); // czy wyswietla  wartosci na osi x

        YAxis zLeftAxis = zChart.getAxisLeft();
        zLeftAxis.setTextColor(Color.WHITE);
        zLeftAxis.setDrawGridLines(false);

        //skala osi y
        zLeftAxis.setAxisMaximum(12f);
        zLeftAxis.setAxisMinimum(-12f);


        zLeftAxis.setDrawGridLines(true);

        YAxis zRightAxis = zChart.getAxisRight();
        zRightAxis.setEnabled(false);


        zChart.getAxisLeft().setDrawGridLines(false);
        zChart.getXAxis().setDrawGridLines(false);
        zChart.setDrawBorders(false);
////////////////////////HR PLOT////////////////////////


        hrChart.setTouchEnabled(false);///// tutaj
        hrChart.setDragEnabled(false);///// tutaj
        hrChart.setScaleEnabled(false);
        hrChart.setDrawGridBackground(false);
        hrChart.setPinchZoom(false); ///// tutaj
        //  hrChart.setBackgroundColor(Color.WHITE);


        Legend hrChartLegend = hrChart.getLegend();
        // hrChartLegend.setForm(Legend.LegendForm.LINE);
        hrChartLegend.setEnabled(false);


        XAxis hrx1 = hrChart.getXAxis();
        hrx1.setTextColor(Color.WHITE);
        hrx1.setDrawGridLines(true);
        hrx1.setAvoidFirstLastClipping(false);//
        hrx1.setEnabled(false); // czy wyswietla  wartosci na osi x


        YAxis hrLeftAxis = hrChart.getAxisLeft();
        hrLeftAxis.setTextColor(Color.WHITE);
        hrLeftAxis.setDrawGridLines(false);

        //skala osi y
        hrLeftAxis.setAxisMaximum(110f);
        hrLeftAxis.setAxisMinimum(60f);


        hrLeftAxis.setDrawGridLines(true);

        YAxis hrRightAxis = hrChart.getAxisRight();
        hrRightAxis.setEnabled(false);

        hrChart.getAxisLeft().setDrawGridLines(false);
        hrChart.getXAxis().setDrawGridLines(false);
        hrChart.setDrawBorders(false);


        LineData hrData = new LineData();
        hrData.setValueTextColor(Color.WHITE);
        hrChart.setData(hrData); //dodaj dane do wykresu


        //PLOTS END


    }


    // metoda pozwolenia
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case 1000:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Permission not granted", Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }


    //metoda zapisująca
    private void saveTextAsFile(String filename, String content) {
        String fileName = filename + ".txt";

        //tworzenie pliku
        File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), fileName);

        //wpisz do pliku
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(content.getBytes());
            fileOutputStream.close();
            Toast.makeText(this, "Saved" + " type: " + measureType, Toast.LENGTH_SHORT).show();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
        }


    }

    public void automaticSave() {




        if (measureTypeSwitch.isChecked()) {
            measureType = "hyperventilation";
        } else {
            measureType = "normal";
        }


        String filename = ID + "_" + measureType;
        String content = listsToString(xAccList, yAccList, zAccList, heartRateList);


        if (!content.equals("")) {

            saveTextAsFile(filename, content);


        } else {
            Toast.makeText(this, "Enter file name", Toast.LENGTH_SHORT).show();
        }
    }


    //watek zapisujacy

    //metody przyciskow

    public void startPressed(View view) {
        clearLists();
        running = true;
    }


    public void stopPressed(View view) {
        running = false;
    }


    public void restartPressed(View view) {
        running = false;
        seconds = measureTime;
        clearLists();

    }


    public void detailsPressed(View view) {

        Intent intent = new Intent(getApplicationContext(), DetailsActivity.class);
        startActivity(intent);

    }


    public void runTimer() {


        timerHandler.post(timerRunnable = new Runnable() {
            @Override
            public void run() {
                int hours = seconds / 3600;
                int minutes = (seconds % 3600) / 60;
                int secs = seconds % 60;


                String time = String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, secs);
                timeOutput.setText(time);


                if (running) {
                    seconds--;
                    addToLists(xAcc, yAcc, zAcc, heartRate);
                }

                timerHandler.postDelayed(this, 500);


                if (seconds == 0) {
                    running = false;
                    timerHandler.removeCallbacksAndMessages(timerRunnable);

                    automaticSave();


                    if (!measureTypeSwitch.isChecked()) {
                        measureTypeSwitch.setChecked(true);
                        seconds = measureTime;
                    }

                }
            }
        });


    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState); // to dopisalem zeby dzialalo
        savedInstanceState.putInt("seconds", seconds);         //wczesniej bylo int
        savedInstanceState.putBoolean("running", running);
        savedInstanceState.putBoolean("wasRunning", wasRunning);
    }

    @Override
    protected void onStop() {
        super.onStop();
        wasRunning = running;
        running = false;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (wasRunning) {
            running = true;
        }
    }

    //metoda wrzucajca dane do list
    public void addToLists(float xAcc, float yAcc, float zAcc, float light) {

        xAccList.add(Double.valueOf(xAcc));
        yAccList.add(Double.valueOf(yAcc));
        zAccList.add(Double.valueOf(zAcc));
        heartRateList.add(Double.valueOf(light));

    }

    public void clearLists() {
        xAccList.clear();
        yAccList.clear();
        zAccList.clear();
        heartRateList.clear();
    }

    public String listsToString(List xAccList, List yAccList, List zAccList, List lightList) {

        String listsString = "";
        String listsStringLine = "";


        if (xAccList.size() == yAccList.size() && yAccList.size() == zAccList.size() && zAccList.size() == lightList.size()) {

            for (int i = 0; i < xAccList.size(); i++) {
                listsStringLine = String.valueOf(i + 1) + '\t' + xAccList.get(i).toString() + '\t' + yAccList.get(i).toString() + '\t' + zAccList.get(i).toString() + '\t' + lightList.get(i).toString() + '\n'; // '\t' wczesniej bylo ";" i bez  toString()
                listsString = listsString + listsStringLine;
            }


            //String.valueOf(i+1) oznacza sekunde pomiaru // DOPRACOWAC GDY POMIAR BEDZIE WYKONYWANY CO 0,5s

        }

        return listsString;
    }


    //wykresy


    private void startPlot() {
        if (messageThread != null) {
            messageThread.interrupt();
        }
        messageThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    plotData = true;


                    try {


                        Thread.sleep(refreshTime);


                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }


                }
            }
        });

        messageThread.start();


    }


    private void addEntry(String message) {

        LineData xData = xChart.getData();
        LineData yData = yChart.getData();
        LineData zData = zChart.getData();
        LineData hrData = hrChart.getData();

        if (xData != null && yData != null && zData != null && hrData != null) {
            ILineDataSet xSet = xData.getDataSetByIndex(0);
            ILineDataSet ySet = yData.getDataSetByIndex(0);
            ILineDataSet zSet = zData.getDataSetByIndex(0);
            ILineDataSet hrSet = xData.getDataSetByIndex(0);

            if (xSet == null && ySet == null && zSet == null && hrSet == null) {
                xSet = createSet();
                ySet = createSet();
                zSet = createSet();
                hrSet = createSet();

                xData.addDataSet(xSet);
                yData.addDataSet(ySet);
                zData.addDataSet(zSet);
                hrData.addDataSet(hrSet);

            }


            messageArray = message.split(";");


            xAcc = Float.valueOf(messageArray[0]);
            yAcc = Float.valueOf(messageArray[1]);
            zAcc = Float.valueOf((messageArray[2]));
            heartRate = Float.valueOf(messageArray[3]);

            String sensorsMessage = String.valueOf(xAcc) + " m/s^2" + '\n' + String.valueOf(yAcc) + " m/s^2" + '\n' + String.valueOf(zAcc) + " m/s^2" + '\n' + String.valueOf(heartRate) + " BPM";
            textview.setText(sensorsMessage);


            xData.addEntry(new Entry(xSet.getEntryCount(), xAcc), 0);
            xData.notifyDataChanged();
            xChart.notifyDataSetChanged(); // wg YT
            xChart.setVisibleXRangeMaximum(pointsOnChart);  //usuwa poprzednie punkty na wykresie
            xChart.moveViewToX(xData.getEntryCount());


            yData.addEntry(new Entry(ySet.getEntryCount(), yAcc), 0);
            yData.notifyDataChanged();
            yChart.notifyDataSetChanged(); // wg YT
            yChart.setVisibleXRangeMaximum(pointsOnChart);
            yChart.moveViewToX(yData.getEntryCount());

//


            zData.addEntry(new Entry(zSet.getEntryCount(), zAcc), 0);
            zData.notifyDataChanged();
            zChart.notifyDataSetChanged(); // wg YT
            zChart.setVisibleXRangeMaximum(pointsOnChart);
            zChart.moveViewToX(zData.getEntryCount());


            hrData.addEntry(new Entry(hrSet.getEntryCount(), heartRate), 0);
            hrData.notifyDataChanged();
            hrChart.notifyDataSetChanged(); // wg YT
            hrChart.setVisibleXRangeMaximum(pointsOnChart);
            hrChart.moveViewToX(hrData.getEntryCount());


        }
    }


    private LineDataSet createSet() {
        //LineDataSet set = new LineDataSet(null, "x acceleration");
        LineDataSet set = new LineDataSet(null, "");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setLineWidth(0.25f);
        set.setColor(Color.WHITE);  // kolor wykresu
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        set.setCubicIntensity(0.2f);
        set.setDrawCircles(false); // nie rysuje kolek
        set.setDrawValues(false); // nie rysuje cyfr
        return set;


    }

    @Override
    protected void onDestroy() {

        messageThread.interrupt();
        super.onDestroy();
    }


    @Override
    protected void onPause() {
        super.onPause();

        if (messageThread != null) {
            messageThread.interrupt();
        }


    }


    @Override
    protected void onResume() {
        super.onResume();

    }

    //ponizej przesyl danych

    public void messageText(String newinfo) {
        if (newinfo.compareTo("") != 0) {
            textview.append("\n" + newinfo);
        }
    }


    public class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // String message = "I just received a message from the wearable " + receivedMessageNumber++;;


            String message = intent.getStringExtra("message");


            if (message.equalsIgnoreCase("finish")) {
                finish();
            } else {


                if (plotData) {
                    addEntry(message);
                    //start watek
                    startPlot();
                    plotData = false;
                }


            }


        }
    }

    //z tego nie korzystamy
    public void talkClick(View v) {
        String message = "Sending message.... ";
        textview.setText(message);
        new NewThread("/my_path", message).start();

    }


    public void sendmessage(String messageText) {
        Bundle bundle = new Bundle();
        bundle.putString("messageText", messageText);
        Message msg = myHandler.obtainMessage();
        msg.setData(bundle);
        myHandler.sendMessage(msg);

    }


    class NewThread extends Thread {
        String path;
        String message;

        NewThread(String p, String m) {
            path = p;
            message = m;
        }


        public void run() {

            Task<List<Node>> wearableList =
                    Wearable.getNodeClient(getApplicationContext()).getConnectedNodes();
            try {

                List<Node> nodes = Tasks.await(wearableList);
                for (Node node : nodes) {
                    Task<Integer> sendMessageTask =
                            Wearable.getMessageClient(MainActivity.this).sendMessage(node.getId(), path, message.getBytes());

                    try {

                        Integer result = Tasks.await(sendMessageTask);
                        sendmessage("I just sent the wearable a message " + sentMessageNumber++);

                    } catch (ExecutionException exception) {

                        //TO DO: Handle the exception//


                    } catch (InterruptedException exception) {

                    }

                }

            } catch (ExecutionException exception) {

                //TO DO: Handle the exception//

            } catch (InterruptedException exception) {

                //TO DO: Handle the exception//
            }

        }
    }
}



