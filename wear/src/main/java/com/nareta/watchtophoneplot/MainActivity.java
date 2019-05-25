package com.nareta.watchtophoneplot;

import android.Manifest;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PermissionInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.lang.reflect.Array;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class MainActivity extends WearableActivity implements SensorEventListener {

    //elementy GUI
    //private TextView text;
    private Button startButton;
    private Button exitButton;
    //  private Button talkButton;



    private TextView hrOutput;


// przesyłanie
    int receivedMessageNumber = 1;
    //int sentMessageNumber = 1;


    //pola
    private boolean isOn = false;

    private float xAcc;
    private float yAcc;
    private float zAcc;
    private float heartRate;

    // czujniki
    private SensorManager sensorManager;
    private Sensor senAccelerometer;
    private Sensor senHeartRate;


    //wiadomosc
    String sensorsMessage = "";
    String accOutputText = "";

    //watek
    Handler waitAndSend = new Handler();
    int delay = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //text =  findViewById(R.id.text);
        // talkButton =  findViewById(R.id.talkClick);
        startButton = findViewById(R.id.startButton);
        exitButton = findViewById(R.id.exitButton);
        hrOutput = findViewById(R.id.hrOutput);




        // Enables Always-on
        setAmbientEnabled();
        //setAmbientOffloadEnabled(true);

        //zgoda na wykorzystanie pulsometru
        String[] per = {Manifest.permission.BODY_SENSORS};
        if (ContextCompat.checkSelfPermission(this, per[0]) != 0) {

            ActivityCompat.requestPermissions(this, per, PermissionInfo.PROTECTION_DANGEROUS);
            Log.d("PERMISSIONS:", "requested, " + ContextCompat.checkSelfPermission(this, per[0]));


        }


        //managery czujnikow
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        senAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        senHeartRate = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        sensorManager.registerListener(this, senAccelerometer, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, senHeartRate, SensorManager.SENSOR_DELAY_GAME);


//Register the local broadcast receiver//

        IntentFilter newFilter = new IntentFilter(Intent.ACTION_SEND);
//        Receiver messageReceiver = new Receiver();
//        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, newFilter);


        // Enables Always-on
        setAmbientEnabled();





    }


    //METODY PRZYCISKOW

    public void startButtonPressed(View view) {


        if (isOn) {
            isOn = false;
            startButton.setText("STOP");

            boolean sensorRegistered = sensorManager.registerListener(this,
                    sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE),
                    SensorManager.SENSOR_DELAY_GAME);
            Log.d("Sensor Status:", " Sensor registered: " + sensorRegistered);

            messageRunnable.run();

        } else {
            isOn = true;
            startButton.setText("START");

            sensorManager.unregisterListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE));

            waitAndSend.removeCallbacks(messageRunnable);

        }
    }


    public void exitButtonPressed(View view) {


        String datapath = "/my_path";
        String exitMessage = "finish";
        new SendMessage(datapath, exitMessage).start();
        sensorManager.unregisterListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE));
        sensorManager.unregisterListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION));

        finish();


    }


    //CZUJNIKI


    //watek do aktualizowania danych
    private Runnable messageRunnable = new Runnable() {
        @Override
        public void run() {

            String datapath = "/my_path";
            new SendMessage(datapath, sensorsMessage).start();

            waitAndSend.postDelayed(this, 500);
        }
    };


    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        Sensor mySensor = sensorEvent.sensor;

        if (mySensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {


            xAcc = sensorEvent.values[0];
            yAcc = sensorEvent.values[1];
            zAcc = sensorEvent.values[2];

            accOutputText = "x=" + xAcc + '\n' + "y=" + yAcc + '\n' + "z=" + zAcc;

        }

        if (mySensor.getType() == Sensor.TYPE_HEART_RATE) {

            heartRate = sensorEvent.values[0];
            hrOutput.setText(heartRate + " BMP");

        }

        //sensorsMessage = accOutputText + '\n' + "heart rate " + heartRate + " BPM";
        sensorsMessage = xAcc + ";" + yAcc + ";" + zAcc + ";" + heartRate;


    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
//PRZESYL DANYCH


//    public class Receiver extends BroadcastReceiver {
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            String onMessageReceived = "I just received a  message from the handheld " + receivedMessageNumber++;
//
//
//
//
//        }
//    }

    class SendMessage extends Thread {
        String path;
        String message;

//Constructor///

        SendMessage(String p, String m) {
            path = p;
            message = m;
        }

//Send the message via the thread. This will send the message to all the currently-connected devices//

        public void run() {

//Get all the nodes//

            Task<List<Node>> nodeListTask =
                    Wearable.getNodeClient(getApplicationContext()).getConnectedNodes();
            try {

//Block on a task and get the result synchronously//

                List<Node> nodes = Tasks.await(nodeListTask);

//Send the message to each device//

                for (Node node : nodes) {
                    Task<Integer> sendMessageTask =
                            Wearable.getMessageClient(MainActivity.this).sendMessage(node.getId(), path, message.getBytes());

                    try {


                        Integer result = Tasks.await(sendMessageTask);


//Handle the errors//

                    } catch (ExecutionException exception) {

//TO DO//

                    } catch (InterruptedException exception) {

//TO DO//

                    }

                }

            } catch (ExecutionException exception) {

//TO DO//

            } catch (InterruptedException exception) {

//TO DO//

            }
        }
    }
}

