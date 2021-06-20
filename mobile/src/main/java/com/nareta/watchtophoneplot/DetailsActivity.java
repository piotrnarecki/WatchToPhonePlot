package com.nareta.watchtophoneplot;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Environment;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class DetailsActivity extends AppCompatActivity {

    //elementy GUI
    private EditText nameInput;
    private EditText idInput;
    private Switch sexSwitch;
    private Switch headSwitch;
    private EditText ageInput;
    private Button saveButton;

    //pola
    private String ID;
    private String name;
    private String sex;
    private String head;
    private int age;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_details);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); //  aplikacja dziala tylko w pionie


        //referencje
        nameInput = (EditText) findViewById(R.id.nameInput);
        idInput = (EditText) findViewById(R.id.idInput);
        sexSwitch = (Switch) findViewById(R.id.sexSwitch);
        ageInput = (EditText) findViewById(R.id.ageInput);

        headSwitch = (Switch) findViewById(R.id.headSwitch);
        saveButton = (Button) findViewById(R.id.saveButton);


    }

    //metody przycisków ;

    public void saveButtonPressed(View view) {


        if (nameInput != null && ageInput != null ) {
            name = nameInput.getText().toString();

            if (sexSwitch.isChecked()) {
                sex = "female";
            } else {
                sex = "male";
            }

            if (headSwitch.isChecked()) {
                head = "head is damaged";
            } else {
                head = "head is ok";
            }


            age = Integer.valueOf(ageInput.getText().toString());

            ID = (idInput.getText().toString());

            //zapis do pliku
            String fileName = ID + "_details" + ".txt";

            String details = name + '\n' + sex + '\n' + age + '\n' + head ;


            //tworzenie pliku
            File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), fileName);

            //wpisz do pliku
            try {
                FileOutputStream fileOutputStream = new FileOutputStream(file);
                fileOutputStream.write(details.getBytes());
                fileOutputStream.close();
                Toast.makeText(this, "Details saved", Toast.LENGTH_SHORT).show();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
            }

            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            intent.putExtra("ID", ID);
            startActivity(intent);
            finish();

        } else {
            nameInput.setText("Enter data !");


        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {


            Intent goToMainActivity = new Intent(getApplicationContext(), MainActivity.class);
            startActivity(goToMainActivity);


        }
        return super.onKeyDown(keyCode, event);
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
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
        }

    }

}
